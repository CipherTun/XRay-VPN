package xyz.xray.vpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import libXray.LibXray

/**
 * Small native bridge used by the Dart side (lib/native.dart):
 *  - installedApps: launchable apps for the "bypass apps" picker
 *  - moveToBack: keep the activity (and the Dart SSH tunnel) alive on Back
 *  - battery optimisation + system VPN settings shortcuts
 */
class MainActivity : FlutterActivity() {
    private val channelName = "xyz.xray.vpn/native"
    private var channel: MethodChannel? = null
    private var pendingLink: String? = null

    companion object {
        private const val REQUEST_XRAY_VPN = 7314
        @Volatile private var permissionResult: MethodChannel.Result? = null
        @Volatile private var startResult: MethodChannel.Result? = null

        fun completeXrayStart(error: String?) {
            Handler(Looper.getMainLooper()).post {
                val r = startResult
                startResult = null
                if (r == null) return@post
                if (error == null) r.success(null) else r.error("xray_start_failed", error, null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureLink(intent)
        val link = pendingLink
        if (link != null) {
            channel?.invokeMethod("onLink", link)
            pendingLink = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_XRAY_VPN) {
            val r = permissionResult
            permissionResult = null
            r?.success(resultCode == RESULT_OK)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /** vless:// vmess:// trojan:// ss:// hysteria2:// links opened from other apps. */
    private fun captureLink(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) {
            i.dataString?.let { pendingLink = it }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        channel!!.setMethodCallHandler { call, result ->
                when (call.method) {
                    "requestXrayPermission" -> {
                        val prepare = VpnService.prepare(this)
                        if (prepare == null) {
                            result.success(true)
                        } else {
                            permissionResult = result
                            startActivityForResult(prepare, REQUEST_XRAY_VPN)
                        }
                    }
                    "convertShareLink" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        val link = args["text"] as? String ?: ""
                        if (link.isBlank()) {
                            result.error("invalid_link", "Share link is empty", null)
                        } else {
                            Thread {
                                try {
                                    val request = org.json.JSONObject()
                                        .put("apiVersion", 3)
                                        .put("method", "convertShareLinksToXrayJson")
                                        .put("payload", org.json.JSONObject().put("text", link))
                                    val response = LibXray.invoke(request.toString())
                                    runOnUiThread { result.success(response) }
                                } catch (e: Exception) {
                                    runOnUiThread { result.error("libxray_convert", e.message, null) }
                                }
                            }.start()
                        }
                    }
                    "startXray" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        val config = args["config"] as? String ?: ""
                        val proxyOnly = args["proxyOnly"] as? Boolean ?: false
                        if (config.isBlank()) {
                            result.error("invalid_config", "Xray configuration is empty", null)
                        } else if (!proxyOnly && VpnService.prepare(this) != null) {
                            result.error("vpn_permission", "VPN permission must be granted before starting", null)
                        } else if (startResult != null) {
                            result.error("already_starting", "An Xray start request is already pending", null)
                        } else {
                            startResult = result
                            val serviceIntent = Intent(this, XrayVpnService::class.java)
                                .setAction(XrayVpnService.ACTION_START)
                                .putExtra(XrayVpnService.EXTRA_CONFIG, config)
                                .putExtra(XrayVpnService.EXTRA_PROXY_ONLY, proxyOnly)
                                .putStringArrayListExtra(XrayVpnService.EXTRA_DISALLOWED_APPS,
                                    (args["disallowedApps"] as? List<*>)?.filterIsInstance<String>()?.let { ArrayList(it) } ?: arrayListOf())
                                .putExtra(XrayVpnService.EXTRA_BYPASS_LAN, args["bypassLan"] as? Boolean ?: false)
                                .putStringArrayListExtra(XrayVpnService.EXTRA_INCLUDE_ROUTES,
                                    (args["includeRoutes"] as? List<*>)?.filterIsInstance<String>()?.let { ArrayList(it) } ?: arrayListOf("0.0.0.0/0"))
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                                else startService(serviceIntent)
                            } catch (e: Exception) {
                                startResult = null
                                result.error("service_start", e.message, null)
                            }
                        }
                    }
                    "stopXray" -> {
                        try {
                            XrayVpnService.stopEngine(this)
                            result.success(null)
                        } catch (e: Exception) {
                            result.error("xray_stop", e.message, null)
                        }
                    }
                    "pingXray" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        val config = args["config"] as? String ?: ""
                        val url = args["url"] as? String ?: "https://cp.cloudflare.com/generate_204"
                        Thread {
                            try {
                                val item = org.json.JSONObject().put("xrayJson", config).put("outboundTag", "proxy")
                                val payload = org.json.JSONObject()
                                    .put("configs", org.json.JSONArray().put(item))
                                    .put("timeout", 5).put("url", url)
                                val response = LibXray.invoke(org.json.JSONObject()
                                    .put("apiVersion", 3).put("method", "pingBatch").put("payload", payload).toString())
                                runOnUiThread { result.success(response) }
                            } catch (e: Exception) {
                                runOnUiThread { result.error("libxray_ping", e.message, null) }
                            }
                        }.start()
                    }
                    "xrayVersion" -> {
                        Thread {
                            try {
                                val response = LibXray.invoke(org.json.JSONObject()
                                    .put("apiVersion", 3).put("method", "xrayVersion").put("payload", org.json.JSONObject()).toString())
                                runOnUiThread { result.success(response) }
                            } catch (e: Exception) {
                                runOnUiThread { result.error("libxray_version", e.message, null) }
                            }
                        }.start()
                    }
                    "takeLink" -> {
                        val l = pendingLink
                        pendingLink = null
                        result.success(l)
                    }
                    "moveToBack" -> {
                        moveTaskToBack(true)
                        result.success(true)
                    }
                    "installedApps" -> {
                        Thread {
                            try {
                                val pm = packageManager
                                val intent = Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_LAUNCHER)
                                val seen = HashSet<String>()
                                val out = ArrayList<Map<String, String>>()
                                for (ri in pm.queryIntentActivities(intent, 0)) {
                                    val pkg = ri.activityInfo.packageName
                                    if (pkg == packageName || !seen.add(pkg)) continue
                                    out.add(
                                        mapOf(
                                            "package" to pkg,
                                            "label" to ri.loadLabel(pm).toString()
                                        )
                                    )
                                }
                                out.sortBy { it["label"]!!.lowercase() }
                                runOnUiThread { result.success(out) }
                            } catch (e: Exception) {
                                runOnUiThread { result.error("apps", e.message, null) }
                            }
                        }.start()
                    }
                    "isIgnoringBatteryOptimizations" -> {
                        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                        result.success(pm.isIgnoringBatteryOptimizations(packageName))
                    }
                    "requestIgnoreBatteryOptimizations" -> {
                        try {
                            val i = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(i)
                            result.success(true)
                        } catch (e: Exception) {
                            result.success(false)
                        }
                    }
                    "openVpnSettings" -> {
                        try {
                            val action = if (Build.VERSION.SDK_INT >= 24)
                                Settings.ACTION_VPN_SETTINGS else "android.net.vpn.SETTINGS"
                            startActivity(Intent(action))
                            result.success(true)
                        } catch (e: Exception) {
                            result.success(false)
                        }
                    }
                    "stopAllDaemons" -> {
                        stopAllNativeDaemons()
                        result.success(true)
                    }
                    "startDnstt" -> {
                        stopAllNativeDaemons()
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        val domain = args["domain"] as? String ?: ""
                        val pubkey = args["pubkey"] as? String ?: ""
                        val transport = args["transport"] as? String ?: "udp"
                        val resolver = args["resolver"] as? String ?: "1.1.1.1:53"
                        val localPort = (args["localPort"] as? String)?.toIntOrNull() ?: 1080
                        android.util.Log.i("MainActivity", "Starting DNSTT engine on 127.0.0.1:$localPort -> $domain ($transport via $resolver)")
                        try {
                            val clazz = Class.forName("dnstt.Dnstt")
                            val method = clazz.getMethod("startClient", String::class.java, String::class.java, String::class.java, String::class.java, Long::class.javaPrimitiveType)
                            method.invoke(null, domain, pubkey, transport, resolver, localPort.toLong())
                        } catch (e: ClassNotFoundException) {
                            android.util.Log.d("MainActivity", "DNSTT AAR not bundled, running in standalone mode")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "DNSTT start failed: ${e.message}")
                        }
                        result.error("engine_missing", "DNSTT engine is not bundled; refusing fake success.", null)
                    }
                    "stopDnstt" -> {
                        android.util.Log.i("MainActivity", "Stopping DNSTT engine")
                        try {
                            val clazz = Class.forName("dnstt.Dnstt")
                            val method = clazz.getMethod("stopClient")
                            method.invoke(null)
                        } catch (_: Exception) {}
                        result.success(true)
                    }
                    "startSlowDns" -> {
                        stopAllNativeDaemons()
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        val ns = args["ns"] as? String ?: ""
                        val pubkey = args["pubkey"] as? String ?: ""
                        val dns = args["dns"] as? String ?: "8.8.8.8"
                        val dnsPort = (args["dnsPort"] as? String)?.toIntOrNull() ?: 53
                        val localPort = (args["localPort"] as? String)?.toIntOrNull() ?: 1080
                        val compress = args["compress"] as? Boolean ?: true
                        android.util.Log.i("MainActivity", "Starting SlowDNS engine on 127.0.0.1:$localPort -> NS: $ns via $dns:$dnsPort")
                        try {
                            val clazz = Class.forName("slowdns.Slowdns")
                            val method = clazz.getMethod("startClient", String::class.java, String::class.java, String::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                            method.invoke(null, ns, pubkey, dns, dnsPort.toLong(), localPort.toLong(), compress)
                        } catch (e: ClassNotFoundException) {
                            android.util.Log.d("MainActivity", "SlowDNS AAR not bundled, running in standalone mode")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "SlowDNS start failed: ${e.message}")
                        }
                        result.error("engine_missing", "SlowDNS engine is not bundled; refusing fake success.", null)
                    }
                    "stopSlowDns" -> {
                        android.util.Log.i("MainActivity", "Stopping SlowDNS engine")
                        try {
                            val clazz = Class.forName("slowdns.Slowdns")
                            val method = clazz.getMethod("stopClient")
                            method.invoke(null)
                        } catch (_: Exception) {}
                        result.success(true)
                    }
                    "startOpenConnect" -> {
                        stopAllNativeDaemons()
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        val host = args["host"] as? String ?: ""
                        val port = (args["port"] as? String)?.toIntOrNull() ?: 443
                        val user = args["user"] as? String ?: ""
                        val pass = args["pass"] as? String ?: ""
                        val cert = args["servercert"] as? String ?: ""
                        val authgroup = args["authgroup"] as? String ?: ""
                        val cookie = args["cookie"] as? String ?: ""
                        android.util.Log.i("MainActivity", "Starting OpenConnect VPN client to $host:$port")
                        try {
                            val clazz = Class.forName("openconnect.Openconnect")
                            val method = clazz.getMethod("startClient", String::class.java, Long::class.javaPrimitiveType, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)
                            method.invoke(null, host, port.toLong(), user, pass, cert, authgroup, cookie)
                        } catch (e: ClassNotFoundException) {
                            android.util.Log.d("MainActivity", "OpenConnect AAR not bundled, running in standalone mode")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "OpenConnect start failed: ${e.message}")
                        }
                        result.error("engine_missing", "OpenConnect engine is not bundled; refusing fake success.", null)
                    }
                    "stopOpenConnect" -> {
                        android.util.Log.i("MainActivity", "Stopping OpenConnect client")
                        try {
                            val clazz = Class.forName("openconnect.Openconnect")
                            val method = clazz.getMethod("stopClient")
                            method.invoke(null)
                        } catch (_: Exception) {}
                        result.success(true)
                    }
                    "startPsiphon" -> {
                        stopAllNativeDaemons()
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        val egress = args["egress"] as? String ?: "AUTO"
                        val socksPort = (args["socksPort"] as? String)?.toIntOrNull() ?: 10808
                        val httpPort = (args["httpPort"] as? String)?.toIntOrNull() ?: 8080
                        android.util.Log.i("MainActivity", "Starting Psiphon Tunnel Core ($egress) on SOCKS:$socksPort HTTP:$httpPort")
                        try {
                            val clazz = Class.forName("psi.Psi")
                            val method = clazz.getMethod("start", String::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                            method.invoke(null, egress, socksPort.toLong(), httpPort.toLong())
                        } catch (e: ClassNotFoundException) {
                            android.util.Log.d("MainActivity", "Psiphon AAR not bundled, running in standalone mode")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Psiphon start failed: ${e.message}")
                        }
                        result.error("engine_missing", "Psiphon engine is not bundled; refusing fake success.", null)
                    }
                    "stopPsiphon" -> {
                        android.util.Log.i("MainActivity", "Stopping Psiphon tunnel")
                        try {
                            val clazz = Class.forName("psi.Psi")
                            val method = clazz.getMethod("stop")
                            method.invoke(null)
                        } catch (_: Exception) {}
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun stopAllNativeDaemons() {
        try {
            Class.forName("dnstt.Dnstt").getMethod("stopClient").invoke(null)
        } catch (_: Exception) {}
        try {
            Class.forName("slowdns.Slowdns").getMethod("stopClient").invoke(null)
        } catch (_: Exception) {}
        try {
            Class.forName("openconnect.Openconnect").getMethod("stopClient").invoke(null)
        } catch (_: Exception) {}
        try {
            Class.forName("psi.Psi").getMethod("stop").invoke(null)
        } catch (_: Exception) {}
    }
}
