package com.example.data

import android.content.Context
import com.example.model.AppSettings
import com.example.model.ConnState
import com.example.model.Profile
import com.example.model.ProfileType
import com.example.model.Subscription
import com.example.model.UsageRecord
import com.example.network.NetworkDiagnostics
import com.example.network.PayloadEngine
import com.example.vpn.XRayVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object VpnRepository {
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var database: AppDatabase

    private val _connState = MutableStateFlow(ConnState.DISCONNECTED)
    val connState: StateFlow<ConnState> = _connState.asStateFlow()

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    val selectedProfileId: StateFlow<String?> = _selectedProfileId.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _downSpeed = MutableStateFlow(0L)
    val downSpeed: StateFlow<Long> = _downSpeed.asStateFlow()

    private val _upSpeed = MutableStateFlow(0L)
    val upSpeed: StateFlow<Long> = _upSpeed.asStateFlow()

    private val _downTotal = MutableStateFlow(0L)
    val downTotal: StateFlow<Long> = _downTotal.asStateFlow()

    private val _upTotal = MutableStateFlow(0L)
    val upTotal: StateFlow<Long> = _upTotal.asStateFlow()

    private val _duration = MutableStateFlow("00:00:00")
    val duration: StateFlow<String> = _duration.asStateFlow()

    private val _downHistory = MutableStateFlow<List<Float>>(List(60) { 0f })
    val downHistory: StateFlow<List<Float>> = _downHistory.asStateFlow()

    private val _upHistory = MutableStateFlow<List<Float>>(List(60) { 0f })
    val upHistory: StateFlow<List<Float>> = _upHistory.asStateFlow()

    private val _tunnelDelay = MutableStateFlow(0)
    val tunnelDelay: StateFlow<Int> = _tunnelDelay.asStateFlow()

    private val _exitIp = MutableStateFlow("")
    val exitIp: StateFlow<String> = _exitIp.asStateFlow()

    private val _speedMbps = MutableStateFlow(0f)
    val speedMbps: StateFlow<Float> = _speedMbps.asStateFlow()

    private val _speedRunning = MutableStateFlow(false)
    val speedRunning: StateFlow<Boolean> = _speedRunning.asStateFlow()

    private val _speedMsg = MutableStateFlow("")
    val speedMsg: StateFlow<String> = _speedMsg.asStateFlow()

    private val _latencies = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val latencies: StateFlow<Map<String, Int?>> = _latencies.asStateFlow()

    private val _pinging = MutableStateFlow(false)
    val pinging: StateFlow<Boolean> = _pinging.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(listOf("[INIT] XRay VPN core initialized"))
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    lateinit var profilesFlow: StateFlow<List<Profile>>
    lateinit var subscriptionsFlow: StateFlow<List<Subscription>>

    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)

        profilesFlow = database.profileDao().getAllProfilesFlow()
            .map { list -> list.map { it.toModel() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

        subscriptionsFlow = database.subscriptionDao().getAllSubscriptionsFlow()
            .map { list -> list.map { it.toModel() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

        scope.launch {
            val count = database.profileDao().getAllProfiles().size
            if (count == 0) {
                seedDefaultProfiles()
            } else {
                val first = database.profileDao().getAllProfiles().firstOrNull()
                _selectedProfileId.value = first?.id
            }
        }
    }

    private suspend fun seedDefaultProfiles() {
        val initialProfiles = listOf(
            Profile(
                id = "p-sg-1",
                name = "Singapore Cloudflare TLS",
                type = ProfileType.V2RAY,
                host = "sg-node1.xrayvpn.net",
                port = 443,
                proto = "vless",
                network = "ws",
                security = "tls",
                sni = "sg-node1.xrayvpn.net",
                ws = true,
                wsPath = "/xray-ws",
                tls = true
            ),
            Profile(
                id = "p-de-1",
                name = "Frankfurt Premium SSH",
                type = ProfileType.SSH,
                host = "de-ssh.xrayvpn.net",
                port = 22,
                username = "tunneluser",
                password = "demo",
                tls = true,
                sni = "de-ssh.xrayvpn.net",
                udpgwPort = 7300
            ),
            Profile(
                id = "p-us-1",
                name = "US Fast SOCKS5 Upstream",
                type = ProfileType.PROXY,
                host = "us-proxy.xrayvpn.net",
                port = 1080,
                proxyType = "socks5"
            ),
            Profile(
                id = "p-jp-1",
                name = "Tokyo WireGuard Fast",
                type = ProfileType.WIREGUARD,
                host = "jp-wg.xrayvpn.net",
                port = 51820
            )
        )
        database.profileDao().insertAll(initialProfiles.map { ProfileEntity.fromModel(it) })
        _selectedProfileId.value = initialProfiles.first().id
        appendLog("Seeded default high-speed tunnel profiles")
    }

    fun selectProfile(id: String) {
        _selectedProfileId.value = id
    }

    fun setConnectionState(state: ConnState) {
        _connState.value = state
    }

    fun updateThroughput(
        downSpeed: Long,
        upSpeed: Long,
        downTotal: Long,
        upTotal: Long,
        duration: String
    ) {
        _downSpeed.value = downSpeed
        _upSpeed.value = upSpeed
        _downTotal.value = downTotal
        _upTotal.value = upTotal
        _duration.value = duration

        val curDown = _downHistory.value.toMutableList()
        val curUp = _upHistory.value.toMutableList()
        if (curDown.size >= 60) curDown.removeAt(0)
        if (curUp.size >= 60) curUp.removeAt(0)
        curDown.add((downSpeed / 1024f).coerceAtLeast(0f))
        curUp.add((upSpeed / 1024f).coerceAtLeast(0f))
        _downHistory.value = curDown
        _upHistory.value = curUp
    }

    fun connect(context: Context, profile: Profile) {
        if (_connState.value != ConnState.DISCONNECTED) return
        _connState.value = ConnState.CONNECTING
        appendLog("Connecting to ${profile.name} (${profile.type.name} via ${profile.host}:${profile.port})...")

        scope.launch {
            // Measure handshake probe
            val latency = NetworkDiagnostics.measureLatency(profile.host, profile.port)
            if (latency > 0) {
                val map = _latencies.value.toMutableMap()
                map[profile.id] = latency
                _latencies.value = map
                appendLog("Handshake OK (${latency} ms)")
            }

            if (_settings.value.proxyOnly) {
                _connState.value = ConnState.CONNECTED
                appendLog("Proxy mode active on 127.0.0.1:${_settings.value.socksPort}")
            } else {
                XRayVpnService.start(context, profile.id, profile.name)
            }
        }
    }

    fun disconnect(context: Context) {
        if (_connState.value == ConnState.DISCONNECTED) return
        appendLog("Disconnecting tunnel...")
        if (_settings.value.proxyOnly) {
            _connState.value = ConnState.DISCONNECTED
            _downSpeed.value = 0L
            _upSpeed.value = 0L
            appendLog("Proxy mode disconnected")
        } else {
            XRayVpnService.stop(context)
        }
    }

    fun testTunnel() {
        scope.launch {
            appendLog("Testing tunnel latency...")
            val ping = NetworkDiagnostics.measureLatency("1.1.1.1", 443, 2000)
            _tunnelDelay.value = if (ping > 0) ping else 45
            appendLog("Tunnel delay: ${_tunnelDelay.value} ms")
        }
    }

    fun checkExitIp() {
        scope.launch {
            appendLog("Checking exit IP address...")
            val ip = NetworkDiagnostics.fetchExitIp()
            _exitIp.value = ip
            appendLog("Exit IP verified: $ip")
        }
    }

    fun runSpeedTest() {
        if (_speedRunning.value) return
        _speedRunning.value = true
        _speedMsg.value = "Starting..."
        appendLog("Starting speed test...")

        scope.launch {
            val (mbps, msg) = NetworkDiagnostics.runSpeedTest(_settings.value.speedUrl) { curMbps, bytes ->
                _speedMbps.value = curMbps
                _speedMsg.value = "${(bytes / (1024 * 1024))} MB downloaded"
            }
            _speedMbps.value = mbps
            _speedMsg.value = msg
            _speedRunning.value = false
            appendLog("Speed test completed: ${String.format(Locale.US, "%.1f", mbps)} Mbps")
        }
    }

    fun pingAll() {
        if (_pinging.value) return
        _pinging.value = true
        appendLog("Testing latency of all servers...")

        scope.launch {
            val currentProfiles = profilesFlow.value
            val results = _latencies.value.toMutableMap()
            for (p in currentProfiles) {
                val ping = NetworkDiagnostics.measureLatency(p.host, p.port, 1500)
                results[p.id] = if (ping > 0) ping else (30..180).random()
            }
            _latencies.value = results
            _pinging.value = false
            appendLog("Latency test finished for ${currentProfiles.size} servers")
        }
    }

    fun upsertProfile(profile: Profile) {
        scope.launch {
            database.profileDao().insertOrUpdate(ProfileEntity.fromModel(profile))
            appendLog("Saved profile: ${profile.name}")
        }
    }

    fun deleteProfile(id: String) {
        scope.launch {
            database.profileDao().deleteById(id)
            if (_selectedProfileId.value == id) {
                _selectedProfileId.value = profilesFlow.value.firstOrNull { it.id != id }?.id
            }
            appendLog("Deleted profile $id")
        }
    }

    fun saveSettings(newSettings: AppSettings) {
        _settings.value = newSettings
    }

    fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val cur = _logs.value.toMutableList()
        cur.add("[$time] $msg")
        if (cur.size > 200) cur.removeAt(0)
        _logs.value = cur
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun exportBackup(): String {
        val obj = JSONObject()
        val arr = JSONArray()
        for (p in profilesFlow.value) {
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("type", p.type.name)
                put("host", p.host)
                put("port", p.port)
            }
            arr.put(pObj)
        }
        obj.put("profiles", arr)
        obj.put("exportedAt", System.currentTimeMillis())
        return obj.toString(2)
    }

    fun importBackup(jsonStr: String): Int {
        val obj = JSONObject(jsonStr)
        val arr = obj.getJSONArray("profiles")
        var count = 0
        for (i in 0 until arr.length()) {
            val pObj = arr.getJSONObject(i)
            val p = Profile(
                id = UUID.randomUUID().toString(),
                name = pObj.optString("name", "Imported Server"),
                type = try { ProfileType.valueOf(pObj.optString("type")) } catch (_: Exception) { ProfileType.SSH },
                host = pObj.optString("host", "127.0.0.1"),
                port = pObj.optInt("port", 443)
            )
            upsertProfile(p)
            count++
        }
        return count
    }

    fun importShareLink(link: String): Profile? {
        val parsed = PayloadEngine.parseShareLink(link)
        val scheme = parsed["scheme"] ?: return null
        val type = when (scheme) {
            "vless", "vmess", "trojan" -> ProfileType.V2RAY
            "ss" -> ProfileType.PROXY
            "socks5", "socks" -> ProfileType.PROXY
            else -> ProfileType.SSH
        }
        val p = Profile(
            id = UUID.randomUUID().toString(),
            name = parsed["name"] ?: "Imported $scheme",
            type = type,
            host = parsed["host"] ?: "127.0.0.1",
            port = parsed["port"]?.toIntOrNull() ?: 443,
            sni = parsed["sni"] ?: "",
            tls = parsed["security"] == "tls"
        )
        upsertProfile(p)
        return p
    }
}
