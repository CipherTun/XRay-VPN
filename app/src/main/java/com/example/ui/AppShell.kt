package com.example.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.VpnRepository
import com.example.model.ConnState
import com.example.ui.home.HomeScreen
import com.example.ui.servers.ServersScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.tools.ToolsScreen
import kotlinx.coroutines.launch

@Composable
fun AppShell(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val profiles by VpnRepository.profilesFlow.collectAsState()
    val connState by VpnRepository.connState.collectAsState()

    // Back handling: Tab != 0 -> Go to Home; Connected on Home -> moveTaskToBack; else exit
    BackHandler {
        if (selectedTab != 0) {
            selectedTab = 0
        } else if (connState != ConnState.DISCONNECTED) {
            activity?.moveTaskToBack(true)
        } else {
            activity?.finish()
        }
    }

    // Check clipboard on launch for share links
    LaunchedEffect(Unit) {
        val clipText = clipboardManager.getText()?.text?.trim() ?: ""
        if (clipText.contains("://")) {
            val scheme = clipText.substringBefore("://").lowercase()
            if (scheme in listOf("vless", "vmess", "trojan", "ss", "socks5", "socks")) {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Link in clipboard: $scheme",
                        actionLabel = "IMPORT",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val imported = VpnRepository.importShareLink(clipText)
                        if (imported != null) {
                            selectedTab = 1
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_navigation_bar"),
                tonalElevation = 3.dp
            ) {
                // Home
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Security else Icons.Outlined.Security,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") },
                    modifier = Modifier.testTag("nav_home")
                )

                // Servers
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (profiles.isNotEmpty()) {
                                    Badge { Text("${profiles.size}") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.Filled.Dns else Icons.Outlined.Dns,
                                contentDescription = "Servers"
                            )
                        }
                    },
                    label = { Text("Servers") },
                    modifier = Modifier.testTag("nav_servers")
                )

                // Tools
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.Handyman else Icons.Outlined.Handyman,
                            contentDescription = "Tools"
                        )
                    },
                    label = { Text("Tools") },
                    modifier = Modifier.testTag("nav_tools")
                )

                // Settings
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    onNavigateToServers = { selectedTab = 1 },
                    onAddServer = { selectedTab = 1 }
                )
                1 -> ServersScreen()
                2 -> ToolsScreen()
                3 -> SettingsScreen()
            }
        }
    }
}
