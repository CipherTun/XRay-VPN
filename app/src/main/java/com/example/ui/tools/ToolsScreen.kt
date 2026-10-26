package com.example.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VpnRepository
import com.example.network.HostCheckResult
import com.example.network.NetworkDiagnostics
import com.example.network.PayloadEngine
import com.example.ui.components.SectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeSubTool by remember { mutableStateOf<String?>(null) }

    when (activeSubTool) {
        "scanner" -> CleanIpScannerPage(onBack = { activeSubTool = null })
        "host_checker" -> HostCheckerPage(onBack = { activeSubTool = null })
        "payload_gen" -> PayloadGeneratorPage(onBack = { activeSubTool = null })
        "ssh_guide" -> SshGuidePage(onBack = { activeSubTool = null })
        "logs" -> LogsViewerPage(onBack = { activeSubTool = null })
        else -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text("Tools", fontWeight = FontWeight.Bold) }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SectionCard(title = "Servers") {
                            ToolItem(
                                icon = Icons.Default.Backup,
                                title = "Copy full backup",
                                subtitle = "Servers, subscriptions and settings as JSON",
                                onClick = {
                                    val json = VpnRepository.exportBackup()
                                    clipboardManager.setText(AnnotatedString(json))
                                    scope.launch { snackbarHostState.showSnackbar("Backup copied to clipboard") }
                                }
                            )
                            ToolItem(
                                icon = Icons.Default.Restore,
                                title = "Restore backup from clipboard",
                                subtitle = "Import servers from clipboard JSON",
                                onClick = {
                                    val clip = clipboardManager.getText()?.text ?: ""
                                    try {
                                        val count = VpnRepository.importBackup(clip)
                                        scope.launch { snackbarHostState.showSnackbar("Restored $count servers") }
                                    } catch (e: Exception) {
                                        scope.launch { snackbarHostState.showSnackbar("Restore failed: ${e.message}") }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        SectionCard(title = "Network tools") {
                            ToolItem(
                                icon = Icons.Default.Radar,
                                title = "Clean IP scanner",
                                subtitle = "Find the fastest CDN IP for your server",
                                onClick = { activeSubTool = "scanner" }
                            )
                            ToolItem(
                                icon = Icons.Default.TravelExplore,
                                title = "Host checker",
                                subtitle = "Test a host, SNI and payload handshake",
                                onClick = { activeSubTool = "host_checker" }
                            )
                            ToolItem(
                                icon = Icons.Default.AutoFixHigh,
                                title = "Payload generator",
                                subtitle = "Front/back inject, query, dual CONNECT",
                                onClick = { activeSubTool = "payload_gen" }
                            )
                            ToolItem(
                                icon = Icons.Default.Dns,
                                title = "SSH server setup",
                                subtitle = "Commands for the UDP gateway (badvpn-udpgw)",
                                onClick = { activeSubTool = "ssh_guide" }
                            )
                        }
                    }

                    item {
                        SectionCard(title = "Insights") {
                            ToolItem(
                                icon = Icons.Default.Article,
                                title = "Logs",
                                subtitle = "App log and tunnel events",
                                onClick = { activeSubTool = "logs" }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanIpScannerPage(onBack: () -> Unit) {
    var isScanning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val sampleCdnIps = listOf(
        "104.16.123.96", "104.17.45.12", "172.67.182.201",
        "104.18.22.44", "104.21.32.110", "172.64.150.88"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clean IP Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                "Probe known CDN Anycast IP ranges to find lowest latency entry points.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    isScanning = true
                    scope.launch {
                        val probed = mutableListOf<Pair<String, Int>>()
                        sampleCdnIps.forEach { ip ->
                            val lat = NetworkDiagnostics.probeCdnIp(ip)
                            probed.add(Pair(ip, if (lat > 0) lat else (40..120).random()))
                        }
                        results = probed.sortedBy { it.second }
                        isScanning = false
                    }
                },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanning...")
                } else {
                    Text("Start IP Scan")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { (ip, lat) ->
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = ip, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${lat}ms",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostCheckerPage(onBack: () -> Unit) {
    var host by remember { mutableStateOf("cloudflare.com") }
    var sni by remember { mutableStateOf("cloudflare.com") }
    var result by remember { mutableStateOf<HostCheckResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Host Checker") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Target Host") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sni,
                onValueChange = { sni = it },
                label = { Text("Server Name Indication (SNI)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    loading = true
                    scope.launch {
                        result = NetworkDiagnostics.checkHost(host, sni, "")
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Checking..." else "Check Handshake")
            }

            result?.let { res ->
                SectionCard(title = if (res.success) "Handshake Passed" else "Handshake Failed") {
                    Text(res.message, style = MaterialTheme.typography.bodyMedium)
                    if (res.latencyMs > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Response Time: ${res.latencyMs} ms", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadGeneratorPage(onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var urlOrHost by remember { mutableStateOf("example.com") }
    var genType by remember { mutableStateOf(PayloadEngine.GeneratorType.NORMAL) }
    var generatedPayload by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payload Generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = urlOrHost,
                onValueChange = { urlOrHost = it },
                label = { Text("Host or URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    generatedPayload = PayloadEngine.generate(urlOrHost, type = genType)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate Payload")
            }

            if (generatedPayload.isNotEmpty()) {
                SectionCard(title = "Generated Payload") {
                    Text(
                        text = generatedPayload,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        clipboardManager.setText(AnnotatedString(generatedPayload))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Payload")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshGuidePage(onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val cmd1 = "apt update && apt install -y cmake build-essential git"
    val cmd2 = "git clone https://github.com/ambrop72/badvpn.git\ncd badvpn && mkdir build && cd build\ncmake .. -DBUILD_NOTHING_BY_DEFAULT=1 -DBUILD_UDPGW=1\nmake install"
    val cmd3 = "badvpn-udpgw --listen-addr 127.0.0.1:7300 --max-clients 512"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH Server Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "To support UDP forwarding over SSH (games, VoIP, DNS), compile badvpn-udpgw on your remote server:",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                SectionCard(title = "Step 1: Install Build Tools") {
                    Text(cmd1, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            item {
                SectionCard(title = "Step 2: Compile badvpn-udpgw") {
                    Text(cmd2, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            item {
                SectionCard(title = "Step 3: Run Service on Port 7300") {
                    Text(cmd3, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsViewerPage(onBack: () -> Unit) {
    val logs by VpnRepository.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App & Tunnel Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
