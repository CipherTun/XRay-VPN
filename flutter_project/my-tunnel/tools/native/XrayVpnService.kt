package xyz.xray.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import libXray.DialerController
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** Android VPN/TUN host for the upstream XTLS/libXray runtime. */
class XrayVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var detachedTunFd: Int = -1
    private var proxyOnly = false
    private val worker = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?) = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeService = this
        when (intent?.action) {
            ACTION_STOP -> {
                worker.execute { stopRuntime(); stopSelf(startId) }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                proxyOnly = intent.getBooleanExtra(EXTRA_PROXY_ONLY, false)
                val apps = intent.getStringArrayListExtra(EXTRA_DISALLOWED_APPS).orEmpty()
                val routes = intent.getStringArrayListExtra(EXTRA_INCLUDE_ROUTES).orEmpty()
                if (config.isBlank()) {
                    MainActivity.completeXrayStart("Empty Xray configuration")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, notification("Starting Xray core…"))
                worker.execute {
                    try {
                        stopRuntime()
                        var actualConfig = config
                        if (!proxyOnly) {
                            val builder = Builder()
                                .setSession("XRay Vpn")
                                .setMtu(1500)
                                .addAddress("10.111.0.1", 30)
                                .addAddress("fd00:1111:2222::1", 126)
                                .addDnsServer("1.1.1.1")
                                .addDnsServer("2606:4700:4700::1111")
                            if (routes.isEmpty()) {
                                builder.addRoute("0.0.0.0", 0)
                            } else {
                                for (route in routes) {
                                    val parts = route.split('/')
                                    if (parts.size != 2) continue
                                    val prefix = parts[1].toIntOrNull() ?: continue
                                    if (prefix in 0..32) builder.addRoute(parts[0], prefix)
                                }
                            }
                            // Always route IPv6 through the TUN to avoid an IPv6 leak.
                            builder.addRoute("::", 0)
                            for (pkg in apps.distinct()) {
                                try { builder.addDisallowedApplication(pkg) }
                                catch (e: Exception) { Log.w(TAG, "Cannot exclude app $pkg", e) }
                            }
                            tun = builder.establish() ?: throw IllegalStateException("Android refused to establish the VPN TUN interface")
                            detachedTunFd = tun!!.detachFd()
                            tun = null
                            actualConfig = attachTunFd(actualConfig, detachedTunFd)
                        }

                        // These are the documented libXray Android controller hooks.
                        // They are needed for full-device VPN mode to prevent tunnel loops.
                        // Proxy-only mode has no TUN and must not require VPN consent.
                        if (!proxyOnly) {
                            synchronized(CONTROLLER_LOCK) {
                                if (!controllersRegistered) {
                                    LibXray.registerDialerController(SocketProtector)
                                    LibXray.registerListenerController(SocketProtector)
                                    controllersRegistered = true
                                }
                            }
                            LibXray.setDNS(SocketProtector, dnsEndpoint(actualConfig))
                        }
                        val response = invoke("runXray", JSONObject().put("xrayJson", actualConfig))
                        if (response.optBoolean("success") != true) {
                            throw IllegalStateException(response.optString("error", "libXray runXray failed"))
                        }
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification(if (proxyOnly) "Local proxy engine running" else "Xray engine running"))
                        MainActivity.completeXrayStart(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Xray start failed", e)
                        stopRuntime()
                        MainActivity.completeXrayStart(e.message ?: e.javaClass.simpleName)
                        stopSelf(startId)
                    }
                }
                return START_NOT_STICKY
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
    }

    /** libXray requires an IP literal plus port for its process-wide Go DNS resolver. */
    private fun dnsEndpoint(config: String): String {
        return try {
            val servers = JSONObject(config).optJSONObject("dns")?.optJSONArray("servers")
            val first = servers?.opt(0)
            val raw = when (first) {
                is String -> first.trim()
                is JSONObject -> first.optString("address", "").trim()
                else -> ""
            }
            val value = raw.removePrefix("udp://").removePrefix("tcp://")
            val bracketedV6 = Regex("^\\[([0-9a-fA-F:]+)](?::(\\d+))?$").matchEntire(value)
            if (bracketedV6 != null) {
                val host = bracketedV6.groupValues[1]
                val port = bracketedV6.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "53"
                "[$host]:$port"
            } else {
                val hostPort = Regex("^((?:\\d{1,3}\\.){3}\\d{1,3})(?::(\\d+))?$").matchEntire(value)
                if (hostPort != null) {
                    val octets = hostPort.groupValues[1].split('.').map { it.toInt() }
                    val port = hostPort.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "53"
                    val portNumber = port.toIntOrNull()
                    if (octets.any { it !in 0..255 } || portNumber == null || portNumber !in 1..65535) DNS_SERVER
                    else "${hostPort.groupValues[1]}:$port"
                } else DNS_SERVER
            }
        } catch (_: Exception) {
            DNS_SERVER
        }
    }

    private fun attachTunFd(config: String, fd: Int): String {
        val root = JSONObject(config)
        // Xray-core's Android TUN implementation reads xray.tun.fd from root env.
        val env = root.optJSONObject("env") ?: JSONObject().also { root.put("env", it) }
        val xray = env.optJSONObject("xray") ?: JSONObject().also { env.put("xray", it) }
        val tunEnv = xray.optJSONObject("tun") ?: JSONObject().also { xray.put("tun", it) }
        tunEnv.put("fd", fd)
        root.put("inbounds", JSONArray().put(JSONObject()
            .put("tag", "android-tun")
            .put("listen", "0.0.0.0")
            .put("port", 0)
            .put("protocol", "tun")
            .put("settings", JSONObject().put("name", "xray0").put("mtu", 1500))))
        return root.toString()
    }

    private fun invoke(method: String, payload: JSONObject = JSONObject()): JSONObject {
        val request = JSONObject().put("apiVersion", 3).put("method", method).put("payload", payload)
        val raw = LibXray.invoke(request.toString())
        return JSONObject(raw)
    }

    private fun stopRuntime() {
        try {
            val response = invoke("stopXray")
            if (response.optBoolean("success") != true) Log.w(TAG, "libXray stop: ${response.optString("error")}")
        } catch (e: Exception) {
            Log.w(TAG, "libXray stop request failed", e)
        } finally {
            try { LibXray.resetDNS() } catch (e: Exception) { Log.w(TAG, "DNS reset failed", e) }
            val fd = detachedTunFd
            detachedTunFd = -1
            if (fd >= 0) try { Os.close(fd) } catch (e: Exception) { Log.w(TAG, "TUN fd close failed", e) }
            try { tun?.close() } catch (_: Exception) {}
            tun = null
        }
    }

    private fun notification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "VPN connection", NotificationManager.IMPORTANCE_LOW))
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("XRay Vpn")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    override fun onRevoke() {
        // Android has revoked the VPN grant: stop the core and close the TUN now.
        stopRuntime()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopRuntime()
        if (activeService === this) activeService = null
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        @Volatile private var activeService: XrayVpnService? = null
        @Volatile private var controllersRegistered = false
        private val CONTROLLER_LOCK = Any()
        private val SocketProtector = object : DialerController {
            override fun protectFd(fd: Int): Boolean = try {
                activeService?.protect(fd) == true
            } catch (e: Exception) {
                Log.e(TAG, "VpnService.protect($fd) failed", e)
                false
            }
        }

        private const val TAG = "XrayVpnService"
        private const val CHANNEL_ID = "xray_vpn"
        private const val NOTIFICATION_ID = 731
        private const val DNS_SERVER = "1.1.1.1:53"
        const val ACTION_START = "xyz.xray.vpn.action.START_XRAY"
        const val ACTION_STOP = "xyz.xray.vpn.action.STOP_XRAY"
        const val EXTRA_CONFIG = "xray_config"
        const val EXTRA_PROXY_ONLY = "proxy_only"
        const val EXTRA_DISALLOWED_APPS = "disallowed_apps"
        const val EXTRA_INCLUDE_ROUTES = "include_routes"
        const val EXTRA_BYPASS_LAN = "bypass_lan"

        fun stopEngine(context: Context) {
            // Stop synchronously before switching to another Android VPN implementation.
            activeService?.stopRuntime()
            context.stopService(Intent(context, XrayVpnService::class.java))
            try {
                val request = JSONObject().put("apiVersion", 3).put("method", "stopXray").put("payload", JSONObject())
                LibXray.invoke(request.toString())
                LibXray.resetDNS()
            } catch (e: Exception) {
                Log.w(TAG, "Best-effort libXray stop failed", e)
            }
        }
    }
}
