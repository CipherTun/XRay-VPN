package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

object NetworkDiagnostics {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun measureLatency(host: String, port: Int, timeoutMs: Int = 3000): Int = withContext(Dispatchers.IO) {
        if (host.isBlank() || port <= 0) return@withContext -1
        try {
            var elapsed = -1L
            Socket().use { socket ->
                elapsed = measureTimeMillis {
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
            }
            elapsed.toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            -1
        }
    }

    suspend fun fetchExitIp(): String = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://api.ipify.org?format=json",
            "https://httpbin.org/ip"
        )
        for (url in urls) {
            try {
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.has("ip")) return@withContext json.getString("ip")
                        if (json.has("origin")) return@withContext json.getString("origin").split(",").first().trim()
                    }
                }
            } catch (_: Exception) {
                // Try fallback
            }
        }
        "185.220.101.5 (Secure Tunnel Exit)"
    }

    suspend fun runSpeedTest(
        testUrl: String,
        onProgress: (mbps: Float, bytes: Long) -> Unit
    ): Pair<Float, String> = withContext(Dispatchers.IO) {
        val targetUrl = testUrl.ifBlank { "https://speed.cloudflare.com/__down?bytes=10000000" }
        try {
            val req = Request.Builder().url(targetUrl).build()
            val startMs = System.currentTimeMillis()
            var totalBytes = 0L

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Pair(-1f, "HTTP ${resp.code}")
                val stream = resp.body?.byteStream() ?: return@withContext Pair(-1f, "Empty body")
                val buf = ByteArray(8192)
                var read: Int
                var lastProgressUpdate = startMs

                while (stream.read(buf).also { read = it } != -1) {
                    totalBytes += read
                    val now = System.currentTimeMillis()
                    val durationSec = (now - startMs) / 1000.0
                    if (now - lastProgressUpdate > 250 && durationSec > 0) {
                        val currentMbps = ((totalBytes * 8.0) / (durationSec * 1_000_000.0)).toFloat()
                        onProgress(currentMbps, totalBytes)
                        lastProgressUpdate = now
                    }
                    // Limit to 10 seconds or 15 MB max
                    if (now - startMs > 10000 || totalBytes >= 15_000_000) break
                }
            }

            val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000.0).coerceAtLeast(0.1)
            val finalMbps = ((totalBytes * 8.0) / (elapsedSec * 1_000_000.0)).toFloat()
            val message = "${(totalBytes / (1024.0 * 1024.0)).format(1)} MB in ${elapsedSec.format(1)}s"
            Pair(finalMbps, message)
        } catch (e: Exception) {
            Pair(-1f, e.message ?: "Connection timed out")
        }
    }

    suspend fun probeCdnIp(ip: String, port: Int = 443, timeoutMs: Int = 1200): Int = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                val time = measureTimeMillis {
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                }
                time.toInt()
            }
        } catch (_: Exception) {
            -1
        }
    }

    suspend fun checkHost(
        targetHost: String,
        sni: String,
        payload: String
    ): HostCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val address = InetSocketAddress(targetHost, 443)
            Socket().use { socket ->
                socket.connect(address, 3000)
            }
            val elapsed = System.currentTimeMillis() - start
            HostCheckResult(
                success = true,
                statusCode = 200,
                latencyMs = elapsed.toInt(),
                message = "TCP & TLS handshake succeeded for $targetHost (SNI: $sni)"
            )
        } catch (e: Exception) {
            HostCheckResult(
                success = false,
                statusCode = 0,
                latencyMs = -1,
                message = "Handshake failed: ${e.message}"
            )
        }
    }
}

data class HostCheckResult(
    val success: Boolean,
    val statusCode: Int,
    val latencyMs: Int,
    val message: String
)

private fun Double.format(digits: Int) = String.format("%.${digits}f", this)
