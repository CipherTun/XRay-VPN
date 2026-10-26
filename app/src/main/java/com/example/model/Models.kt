package com.example.model

enum class ProfileType {
    SSH,
    V2RAY,
    PROXY,
    WIREGUARD,
    OPENVPN,
    CHAIN,
    GROUP;

    val label: String
        get() = when (this) {
            SSH -> "SSH Tunnel"
            V2RAY -> "V2Ray / Xray / Hysteria2"
            PROXY -> "HTTP / HTTPS / SOCKS5 Proxy"
            WIREGUARD -> "WireGuard"
            OPENVPN -> "OpenVPN"
            CHAIN -> "Proxy Chain (entry → exit)"
            GROUP -> "Failover Group"
        }

    val shortLabel: String
        get() = when (this) {
            SSH -> "SSH"
            V2RAY -> "V2Ray"
            PROXY -> "Proxy"
            WIREGUARD -> "WG"
            OPENVPN -> "OVPN"
            CHAIN -> "Chain"
            GROUP -> "Group"
        }
}

data class Profile(
    val id: String,
    val name: String,
    val type: ProfileType,
    val host: String = "",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val subId: String = "",
    val connectAddr: String = "",
    val tls: Boolean = false,
    val sni: String = "",
    val ws: Boolean = false,
    val wsPath: String = "",
    val proxyType: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val payload: String = "",
    val udpgwPort: Int = 0,
    val rawConfig: String = "",
    val mode: String = "manual",
    val proto: String = "vmess",
    val network: String = "tcp",
    val security: String = "tls",
    val members: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val subtitle: String
        get() = when (type) {
            ProfileType.SSH -> {
                val extras = mutableListOf<String>()
                if (tls) extras.add("TLS/SNI")
                if (proxyHost.isNotEmpty()) extras.add(if (proxyType == "socks5") "SOCKS5 proxy" else "HTTP proxy")
                if (payload.isNotEmpty()) extras.add("payload")
                if (ws) extras.add("WS")
                if (udpgwPort > 0) extras.add("UDP")
                val extraStr = if (extras.isEmpty()) "" else "  ·  " + extras.joinToString(" + ")
                "SSH $host:$port$extraStr"
            }
            ProfileType.V2RAY -> {
                if (rawConfig.isNotEmpty()) {
                    "Raw Xray JSON"
                } else {
                    "${proto.uppercase()} $host:$port  ·  $network/$security"
                }
            }
            ProfileType.PROXY -> {
                val k = if (proxyType.isEmpty()) "http" else proxyType
                "${if (k == "socks5") "SOCKS5" else k.uppercase()} proxy $host:$port"
            }
            ProfileType.WIREGUARD -> "WireGuard"
            ProfileType.OPENVPN -> "OpenVPN $host:$port"
            ProfileType.CHAIN -> "Chain of 2 servers"
            ProfileType.GROUP -> "${members.size} servers, tried in order"
        }

    val hostKey: String
        get() = "$host:${if (port == 0) 22 else port}"
}

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val autoUpdate: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis(),
    val profileCount: Int = 0
)

data class AppSettings(
    val themeMode: String = "system",
    var proxyOnly: Boolean = false,
    val dns1: String = "8.8.8.8",
    val dns2: String = "1.1.1.1",
    val fallbackDns: String = "1.0.0.1",
    val socksPort: Int = 10808,
    val sshKeepalive: Int = 15,
    val hotspotUser: String = "",
    val hotspotPass: String = "",
    val fragLength: String = "100-200",
    val fragInterval: String = "10-20",
    val muxConcurrency: Int = 8,
    val blockQuic: Boolean = true,
    val routeOnlyIps: Boolean = false,
    val sniffing: Boolean = true,
    val speedUrl: String = "https://speed.cloudflare.com/__down?bytes=10000000",
    val bypassApps: Set<String> = emptySet()
)

data class UsageRecord(
    val id: Long = 0,
    val profileId: String,
    val date: String,
    val downBytes: Long,
    val upBytes: Long
)

enum class ConnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}
