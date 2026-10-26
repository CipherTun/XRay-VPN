package com.example.network

object PayloadEngine {
    enum class GeneratorType(val label: String) {
        NORMAL("Normal"),
        FRONT_INJECT("Front Inject"),
        BACK_INJECT("Back Inject"),
        FRONT_QUERY("Front Query"),
        BACK_QUERY("Back Query"),
        DUAL_CONNECT("Dual CONNECT")
    }

    enum class Method(val label: String) {
        CONNECT("CONNECT"),
        GET("GET"),
        POST("POST"),
        HEAD("HEAD"),
        PUT("PUT"),
        TRACE("TRACE"),
        OPTIONS("OPTIONS")
    }

    fun generate(
        urlOrHost: String,
        method: Method = Method.CONNECT,
        type: GeneratorType = GeneratorType.NORMAL,
        dualConnect: Boolean = false,
        keepAlive: Boolean = true,
        userAgent: Boolean = true,
        forwardHost: Boolean = false
    ): String {
        val host = urlOrHost.ifBlank { "example.com" }
            .replace("https://", "")
            .replace("http://", "")
            .trimEnd('/')

        val headers = mutableListOf<String>()
        if (keepAlive) headers.add("Connection: Keep-Alive")
        if (userAgent) headers.add("User-Agent: [ua]")
        if (forwardHost) headers.add("X-Forward-Host: [host]")

        val headersStr = if (headers.isEmpty()) "" else headers.joinToString("[crlf]") + "[crlf]"

        return when (type) {
            GeneratorType.NORMAL -> {
                "${method.name} [host_port] [protocol][crlf]Host: $host[crlf]${headersStr}[crlf]"
            }
            GeneratorType.FRONT_INJECT -> {
                "${method.name} http://$host/ [protocol][crlf]Host: $host[crlf]${headersStr}[crlf]"
            }
            GeneratorType.BACK_INJECT -> {
                "${method.name} [host_port] [protocol][crlf]Host: [host][crlf]X-Online-Host: $host[crlf]${headersStr}[crlf]"
            }
            GeneratorType.FRONT_QUERY -> {
                "${method.name} [host_port]?$host [protocol][crlf]Host: [host][crlf]${headersStr}[crlf]"
            }
            GeneratorType.BACK_QUERY -> {
                "${method.name} [host_port]?query=$host [protocol][crlf]Host: [host][crlf]${headersStr}[crlf]"
            }
            GeneratorType.DUAL_CONNECT -> {
                "CONNECT [host_port] HTTP/1.1[crlf]Host: $host[crlf][crlf]CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf]${headersStr}[crlf]"
            }
        }
    }

    fun parseShareLink(link: String): Map<String, String> {
        val trimmed = link.trim()
        val result = mutableMapOf<String, String>()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        result["scheme"] = scheme
        val body = trimmed.substringAfter("://", "")

        when (scheme) {
            "vless", "vmess", "trojan" -> {
                val atSplit = body.split("@")
                if (atSplit.size >= 2) {
                    result["uuid"] = atSplit[0]
                    val hostPart = atSplit[1].substringBefore("?")
                    val hostPort = hostPart.split(":")
                    result["host"] = hostPort[0]
                    result["port"] = if (hostPort.size > 1) hostPort[1].substringBefore("#") else "443"

                    val fragment = if (body.contains("#")) body.substringAfter("#") else ""
                    result["name"] = if (fragment.isNotEmpty()) fragment else "$scheme-${result["host"]}"

                    if (body.contains("?")) {
                        val query = body.substringAfter("?").substringBefore("#")
                        query.split("&").forEach { param ->
                            val kv = param.split("=")
                            if (kv.size == 2) result[kv[0]] = kv[1]
                        }
                    }
                }
            }
            "ss" -> {
                result["name"] = "Shadowsocks Server"
                result["host"] = body.substringBefore(":").substringAfter("@")
                result["port"] = "8388"
            }
            "socks5", "socks" -> {
                result["name"] = "SOCKS5 Proxy"
                result["host"] = body.substringBefore(":")
                result["port"] = "1080"
            }
        }
        return result
    }
}
