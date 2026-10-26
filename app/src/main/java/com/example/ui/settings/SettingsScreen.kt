package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.VpnRepository
import com.example.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by VpnRepository.settings.collectAsState()

    var dns1 by remember(settings) { mutableStateOf(settings.dns1) }
    var dns2 by remember(settings) { mutableStateOf(settings.dns2) }
    var socksPort by remember(settings) { mutableStateOf(settings.socksPort.toString()) }
    var speedUrl by remember(settings) { mutableStateOf(settings.speedUrl) }

    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Mode
            item {
                SectionCard(title = "Appearance") {
                    Text("Theme mode", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system", "dark", "light").forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { VpnRepository.saveSettings(settings.copy(themeMode = mode)) },
                                label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            // DNS Configuration
            item {
                SectionCard(title = "DNS Configuration") {
                    OutlinedTextField(
                        value = dns1,
                        onValueChange = {
                            dns1 = it
                            VpnRepository.saveSettings(settings.copy(dns1 = it))
                        },
                        label = { Text("Primary DNS") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dns2,
                        onValueChange = {
                            dns2 = it
                            VpnRepository.saveSettings(settings.copy(dns2 = it))
                        },
                        label = { Text("Secondary DNS") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Local Proxy Port
            item {
                SectionCard(title = "Inbound Proxy") {
                    OutlinedTextField(
                        value = socksPort,
                        onValueChange = {
                            socksPort = it
                            it.toIntOrNull()?.let { p ->
                                VpnRepository.saveSettings(settings.copy(socksPort = p))
                            }
                        },
                        label = { Text("Local SOCKS5 Port") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Security & Routing Toggles
            item {
                SectionCard(title = "Routing & Security") {
                    SettingToggle(
                        title = "Block QUIC (UDP 443)",
                        subtitle = "Forces HTTP/3 fallback to TCP to prevent ISP packet drops",
                        checked = settings.blockQuic,
                        onCheckedChange = { VpnRepository.saveSettings(settings.copy(blockQuic = it)) }
                    )
                    SettingToggle(
                        title = "Traffic Sniffing",
                        subtitle = "Inspect TLS SNI and HTTP headers for routing accuracy",
                        checked = settings.sniffing,
                        onCheckedChange = { VpnRepository.saveSettings(settings.copy(sniffing = it)) }
                    )
                }
            }

            // System Battery Exemption
            item {
                SectionCard(title = "System Optimizations") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery Optimization Exemption", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isIgnoringBattery) "App is exempt (Recommended for background VPN)" else "Optimizations active (may terminate tunnel)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isIgnoringBattery) {
                            TextButton(onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }) {
                                Text("Exempt")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
