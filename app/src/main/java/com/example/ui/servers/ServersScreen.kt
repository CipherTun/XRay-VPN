package com.example.ui.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VpnRepository
import com.example.model.ConnState
import com.example.model.Profile
import com.example.model.ProfileType
import com.example.ui.components.LatencyBadge
import com.example.ui.components.SectionCard
import com.example.ui.components.TypeAvatar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val profiles by VpnRepository.profilesFlow.collectAsState()
    val selectedId by VpnRepository.selectedProfileId.collectAsState()
    val connState by VpnRepository.connState.collectAsState()
    val latencies by VpnRepository.latencies.collectAsState()
    val pinging by VpnRepository.pinging.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<ProfileType?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val isLocked = connState != ConnState.DISCONNECTED

    val filteredProfiles = remember(profiles, searchQuery, selectedFilter, latencies) {
        profiles.filter { p ->
            (selectedFilter == null || p.type == selectedFilter) &&
            (searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || p.subtitle.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Servers (${profiles.size})", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { VpnRepository.pingAll() },
                        enabled = !isLocked && !pinging
                    ) {
                        if (pinging) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.NetworkPing, contentDescription = "Test latency of all servers")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!isLocked) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_server_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search servers") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Protocol Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All") }
                    )
                }
                items(ProfileType.values()) { type ->
                    FilterChip(
                        selected = selectedFilter == type,
                        onClick = { selectedFilter = if (selectedFilter == type) null else type },
                        label = { Text(type.shortLabel) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Server List
            if (filteredProfiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No servers found" else "No matching servers",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredProfiles, key = { it.id }) { profile ->
                        ServerListItem(
                            profile = profile,
                            isSelected = profile.id == selectedId,
                            isLocked = isLocked,
                            latency = latencies[profile.id],
                            onSelect = { VpnRepository.selectProfile(profile.id) },
                            onDelete = { VpnRepository.deleteProfile(profile.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newProfile ->
                VpnRepository.upsertProfile(newProfile)
                VpnRepository.selectProfile(newProfile.id)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ServerListItem(
    profile: Profile,
    isSelected: Boolean,
    isLocked: Boolean,
    latency: Int?,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    SectionCard(
        modifier = Modifier.clickable(enabled = !isLocked) { onSelect() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TypeAvatar(type = profile.type)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${profile.type.shortLabel}  ·  ${profile.subtitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            LatencyBadge(latency = latency)
            Spacer(modifier = Modifier.width(4.dp))
            RadioButton(
                selected = isSelected,
                onClick = if (!isLocked) onSelect else null
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AddServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Profile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var selectedType by remember { mutableStateOf(ProfileType.V2RAY) }
    var importText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("manual") } // manual or import

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == "manual") "Add Server" else "Import Link / Config") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { mode = "manual" }) {
                        Text("Manual", fontWeight = if (mode == "manual") FontWeight.Bold else FontWeight.Normal)
                    }
                    TextButton(onClick = { mode = "import" }) {
                        Text("Import Link", fontWeight = if (mode == "import") FontWeight.Bold else FontWeight.Normal)
                    }
                }

                if (mode == "manual") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host or IP") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Protocol selector
                    Text("Protocol:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(ProfileType.V2RAY, ProfileType.SSH, ProfileType.PROXY, ProfileType.WIREGUARD)) { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = { selectedType = t },
                                label = { Text(t.shortLabel) }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("Paste vless://, vmess://, ss://, socks5://") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (mode == "manual") {
                        val p = Profile(
                            id = UUID.randomUUID().toString(),
                            name = name.ifBlank { "Custom ${selectedType.shortLabel}" },
                            type = selectedType,
                            host = host.ifBlank { "127.0.0.1" },
                            port = port.toIntOrNull() ?: 443
                        )
                        onConfirm(p)
                    } else {
                        val imported = VpnRepository.importShareLink(importText)
                        if (imported != null) {
                            onConfirm(imported)
                        }
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
