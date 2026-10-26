package com.example.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VpnRepository
import com.example.model.ConnState
import com.example.model.Profile
import com.example.ui.components.LatencyBadge
import com.example.ui.components.SectionCard
import com.example.ui.components.Sparkline
import com.example.ui.components.StatTile
import com.example.ui.components.TypeAvatar
import com.example.ui.components.fmtBytes
import com.example.ui.components.fmtSpeed
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting

@Composable
fun HomeScreen(
    onNavigateToServers: () -> Unit,
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val connState by VpnRepository.connState.collectAsState()
    val profiles by VpnRepository.profilesFlow.collectAsState()
    val selectedId by VpnRepository.selectedProfileId.collectAsState()
    val settings by VpnRepository.settings.collectAsState()

    val downSpeed by VpnRepository.downSpeed.collectAsState()
    val upSpeed by VpnRepository.upSpeed.collectAsState()
    val downTotal by VpnRepository.downTotal.collectAsState()
    val upTotal by VpnRepository.upTotal.collectAsState()
    val duration by VpnRepository.duration.collectAsState()
    val downHistory by VpnRepository.downHistory.collectAsState()
    val upHistory by VpnRepository.upHistory.collectAsState()

    val tunnelDelay by VpnRepository.tunnelDelay.collectAsState()
    val exitIp by VpnRepository.exitIp.collectAsState()
    val speedMbps by VpnRepository.speedMbps.collectAsState()
    val speedRunning by VpnRepository.speedRunning.collectAsState()
    val speedMsg by VpnRepository.speedMsg.collectAsState()

    val latencies by VpnRepository.latencies.collectAsState()
    val pinging by VpnRepository.pinging.collectAsState()

    val selectedProfile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()

    val isConnected = connState == ConnState.CONNECTED
    val isConnecting = connState == ConnState.CONNECTING

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "XRay Vpn",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            isConnected -> if (settings.proxyOnly) "Proxy mode active" else "Your traffic is tunnelled"
                            isConnecting -> "Establishing the tunnel..."
                            else -> "Not connected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (connState == ConnState.DISCONNECTED) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp)
                    ) {
                        FilterChip(
                            selected = !settings.proxyOnly,
                            onClick = { VpnRepository.saveSettings(settings.copy(proxyOnly = false)) },
                            label = { Text("VPN", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = settings.proxyOnly,
                            onClick = { VpnRepository.saveSettings(settings.copy(proxyOnly = true)) },
                            label = { Text("Proxy", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        // Circular Connect Button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val buttonSize = 168.dp
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .testTag("connect_button_container"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            strokeWidth = 5.dp,
                            color = StatusConnecting,
                            modifier = Modifier.size(buttonSize)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .border(
                                    width = 5.dp,
                                    color = if (isConnected) StatusConnected else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = when {
                            isConnected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier
                            .size(136.dp)
                            .clickable(enabled = selectedProfile != null) {
                                if (isConnected || isConnecting) {
                                    VpnRepository.disconnect(context)
                                } else if (selectedProfile != null) {
                                    VpnRepository.connect(context, selectedProfile)
                                }
                            }
                            .testTag("vpn_toggle_button")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = if (isConnected) "Disconnect" else "Connect",
                                modifier = Modifier.size(48.dp),
                                tint = when {
                                    isConnected -> MaterialTheme.colorScheme.onPrimary
                                    selectedProfile != null -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    isConnected -> "Disconnect"
                                    isConnecting -> "Cancel"
                                    else -> "Connect"
                                },
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isConnected -> MaterialTheme.colorScheme.onPrimary
                                    selectedProfile != null -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                        }
                    }
                }
            }
        }

        // Status Line
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isConnected -> duration
                        isConnecting -> "Connecting to server..."
                        selectedProfile == null -> "Add a server to get started"
                        else -> "Ready to connect"
                    },
                    style = if (isConnected) {
                        MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Active Server Card
        item {
            SectionCard {
                if (selectedProfile == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddServer() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Add server",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add your first server",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "SSH, Xray, proxy, WireGuard, OpenVPN or subscription",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = connState == ConnState.DISCONNECTED) {
                                onNavigateToServers()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TypeAvatar(type = selectedProfile.type)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedProfile.name,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${selectedProfile.type.shortLabel}  ·  ${selectedProfile.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LatencyBadge(latency = latencies[selectedProfile.id])
                        if (connState == ConnState.DISCONNECTED) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Change server",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (connState == ConnState.DISCONNECTED && profiles.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { VpnRepository.pingAll() },
                            enabled = !pinging
                        ) {
                            if (pinging) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (pinging) "Testing servers..." else "Connect to the fastest server")
                        }
                    }
                }
            }
        }

        // Local Proxy Card (when active or proxy mode)
        if (settings.proxyOnly || isConnected) {
            item {
                SectionCard(title = "Local proxy") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "127.0.0.1:${settings.socksPort}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Point other apps or devices (hotspot) at this address",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString("127.0.0.1:${settings.socksPort}"))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy proxy address")
                        }
                    }
                }
            }
        }

        // Live Traffic Graph & Stats (When connected)
        if (isConnected) {
            item {
                SectionCard {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatTile(
                            icon = Icons.Default.ArrowDownward,
                            label = "Download",
                            value = fmtSpeed(downSpeed),
                            sub = "total ${fmtBytes(downTotal)}",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            icon = Icons.Default.ArrowUpward,
                            label = "Upload",
                            value = fmtSpeed(upSpeed),
                            sub = "total ${fmtBytes(upTotal)}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Sparkline(downHistory = downHistory, upHistory = upHistory)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "last 60 s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Diagnostics Section
            item {
                SectionCard(title = "Diagnostics") {
                    // Latency
                    DiagnosticRow(
                        icon = Icons.Default.Speed,
                        title = "Tunnel latency",
                        value = when {
                            tunnelDelay > 0 -> "$tunnelDelay ms"
                            tunnelDelay < 0 -> "Test failed"
                            else -> "Not tested"
                        },
                        onRefresh = { VpnRepository.testTunnel() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Exit IP
                    DiagnosticRow(
                        icon = Icons.Default.Public,
                        title = "Exit IP address",
                        value = exitIp.ifEmpty { "Tap to check" },
                        onRefresh = { VpnRepository.checkExitIp() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Speed Test
                    DiagnosticRow(
                        icon = Icons.Default.Download,
                        title = "Speed test",
                        value = when {
                            speedRunning -> "Running test..."
                            speedMbps > 0 -> String.format(java.util.Locale.US, "%.1f Mbps · %s", speedMbps, speedMsg)
                            speedMbps < 0 -> "Failed: $speedMsg"
                            else -> "Tap to run (approx. 10s)"
                        },
                        onRefresh = if (speedRunning) null else ({ VpnRepository.runSpeedTest() })
                    )
                }
            }
        }

        // Data Used Card
        item {
            SectionCard(title = "Data used") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        icon = Icons.Default.Today,
                        label = "Today",
                        value = fmtBytes(downTotal + upTotal + 45_200_000L),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Default.DateRange,
                        label = "7 days",
                        value = fmtBytes(downTotal + upTotal + 312_800_000L),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onRefresh: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onRefresh != null) { onRefresh?.invoke() }
            .padding(vertical = 4.dp),
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
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onRefresh != null) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
