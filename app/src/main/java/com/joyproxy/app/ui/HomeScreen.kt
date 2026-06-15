package com.joyproxy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.joyproxy.app.config.DnsMode
import com.joyproxy.app.config.ProxyProtocol
import com.joyproxy.app.config.ProxyScope
import com.joyproxy.app.config.ProxySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onConnect: () -> Unit,
    onPickApps: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JoyProxy") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionCard(
                connected = settings.connected,
                onConnect = {
                    viewModel.connect(onConnect)
                },
                onDisconnect = viewModel::disconnect,
            )

            ProxyConfigCard(
                settings = settings,
                onProtocolChange = viewModel::setProtocol,
                onHostChange = viewModel::setHost,
                onPortChange = viewModel::setPort,
                onUsernameChange = viewModel::setUsername,
                onPasswordChange = viewModel::setPassword,
            )

            ScopeCard(
                scope = settings.scope,
                selectedCount = settings.selectedApps.size,
                onScopeChange = viewModel::setScope,
                onPickApps = onPickApps,
            )

            DnsCard(
                dnsMode = settings.dnsMode,
                customDns = settings.customDns,
                dohUrl = settings.dohUrl,
                onDnsModeChange = viewModel::setDnsMode,
                onCustomDnsChange = viewModel::setCustomDns,
                onDohUrlChange = viewModel::setDohUrl,
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    connected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Text(
                    text = if (connected) "已连接" else "未连接",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Button(
                onClick = if (connected) onDisconnect else onConnect,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (connected) Color(0xFFE53935) else Color(0xFF1976D2),
                    ),
            ) {
                Text(if (connected) "断开连接" else "连接代理")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyConfigCard(
    settings: ProxySettings,
    onProtocolChange: (ProxyProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
) {
    SectionCard(title = "代理服务器") {
        var protocolExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = protocolExpanded, onExpandedChange = { protocolExpanded = it }) {
            OutlinedTextField(
                value = settings.protocol.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("协议") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = protocolExpanded, onDismissRequest = { protocolExpanded = false }) {
                ProxyProtocol.entries.forEach { protocol ->
                    DropdownMenuItem(
                        text = { Text(protocol.name) },
                        onClick = {
                            onProtocolChange(protocol)
                            protocolExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = settings.host,
            onValueChange = onHostChange,
            label = { Text("地址 (IP 或域名)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = settings.port.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let(onPortChange) },
            label = { Text("端口") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = settings.username,
            onValueChange = onUsernameChange,
            label = { Text("用户名 (可选)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = settings.password,
            onValueChange = onPasswordChange,
            label = { Text("密码 (可选)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeCard(
    scope: ProxyScope,
    selectedCount: Int,
    onScopeChange: (ProxyScope) -> Unit,
    onPickApps: () -> Unit,
) {
    SectionCard(title = "代理范围") {
        var expanded by remember { mutableStateOf(false) }
        val scopeLabel =
            when (scope) {
                ProxyScope.GLOBAL -> "全局 (所有应用)"
                ProxyScope.WHITELIST -> "白名单 (仅选中应用)"
                ProxyScope.BLACKLIST -> "黑名单 (排除选中应用)"
            }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = scopeLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ProxyScope.entries.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (item) {
                                    ProxyScope.GLOBAL -> "全局"
                                    ProxyScope.WHITELIST -> "白名单"
                                    ProxyScope.BLACKLIST -> "黑名单"
                                },
                            )
                        },
                        onClick = {
                            onScopeChange(item)
                            expanded = false
                        },
                    )
                }
            }
        }

        if (scope != ProxyScope.GLOBAL) {
            Button(onClick = onPickApps, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Text("选择应用 ($selectedCount)", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsCard(
    dnsMode: DnsMode,
    customDns: String,
    dohUrl: String,
    onDnsModeChange: (DnsMode) -> Unit,
    onCustomDnsChange: (String) -> Unit,
    onDohUrlChange: (String) -> Unit,
) {
    SectionCard(title = "DNS 设置") {
        var expanded by remember { mutableStateOf(false) }
        val label =
            when (dnsMode) {
                DnsMode.FAKE_IP -> "Fake-IP (推荐，防 DNS 污染)"
                DnsMode.DOH -> "安全 DNS (DoH)"
                DnsMode.CUSTOM -> "自定义 DNS"
                DnsMode.SYSTEM -> "系统默认"
            }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DnsMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (mode) {
                                    DnsMode.FAKE_IP -> "Fake-IP (推荐)"
                                    DnsMode.DOH -> "安全 DNS (DoH)"
                                    DnsMode.CUSTOM -> "自定义 DNS"
                                    DnsMode.SYSTEM -> "系统默认"
                                },
                            )
                        },
                        onClick = {
                            onDnsModeChange(mode)
                            expanded = false
                        },
                    )
                }
            }
        }

        if (dnsMode == DnsMode.CUSTOM) {
            OutlinedTextField(
                value = customDns,
                onValueChange = onCustomDnsChange,
                label = { Text("DNS 服务器") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        if (dnsMode == DnsMode.DOH || dnsMode == DnsMode.FAKE_IP) {
            OutlinedTextField(
                value = dohUrl,
                onValueChange = onDohUrlChange,
                label = { Text("DoH 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}
