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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    val testState by viewModel.testState.collectAsState()
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
                onConnect = { viewModel.connect(onConnect) },
                onDisconnect = viewModel::disconnect,
            )

            ProxyConfigCard(
                settings = settings,
                testState = testState,
                onProtocolChange = viewModel::setProtocol,
                onHostChange = viewModel::setHost,
                onPortChange = viewModel::setPort,
                onUsernameChange = viewModel::setUsername,
                onPasswordChange = viewModel::setPassword,
                onTest = viewModel::testProxy,
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
    testState: ProxyTestState,
    onProtocolChange: (ProxyProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    var host by remember { mutableStateOf(settings.host) }
    var portText by remember { mutableStateOf(settings.port.toString()) }
    var username by remember { mutableStateOf(settings.username) }
    var password by remember { mutableStateOf(settings.password) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(settings.host) { if (host != settings.host) host = settings.host }
    LaunchedEffect(settings.username) { if (username != settings.username) username = settings.username }
    LaunchedEffect(settings.password) { if (password != settings.password) password = settings.password }

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
            value = host,
            onValueChange = {
                host = it
                onHostChange(it)
            },
            label = { Text("地址 (IP 或域名)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = portText,
            onValueChange = {
                portText = it.filter { ch -> ch.isDigit() }
                portText.toIntOrNull()?.let { port ->
                    if (port in 1..65535) onPortChange(port)
                }
            },
            label = { Text("端口") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                onUsernameChange(it)
            },
            label = { Text("用户名 (可选)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                onPasswordChange(it)
            },
            label = { Text("密码 (可选)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    )
                }
            },
        )

        OutlinedButton(
            onClick = onTest,
            enabled = testState.status != ProxyTestStatus.Testing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (testState.status == ProxyTestStatus.Testing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(if (testState.status == ProxyTestStatus.Testing) "测试中…" else "测试代理连通性")
        }

        if (testState.status == ProxyTestStatus.Success || testState.status == ProxyTestStatus.Failed) {
            Text(
                text = testState.message,
                color = if (testState.status == ProxyTestStatus.Success) Color(0xFF2E7D32) else Color(0xFFC62828),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
    var customDnsText by remember { mutableStateOf(customDns) }
    var dohUrlText by remember { mutableStateOf(dohUrl) }

    LaunchedEffect(customDns) { if (customDnsText != customDns) customDnsText = customDns }
    LaunchedEffect(dohUrl) { if (dohUrlText != dohUrl) dohUrlText = dohUrl }

    SectionCard(title = "DNS 设置") {
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = dnsModeLabel(dnsMode),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(
                    DnsMode.FAKE_IP,
                    DnsMode.DOH,
                    DnsMode.CUSTOM,
                    DnsMode.SYSTEM,
                ).forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(dnsModeLabel(mode, short = true)) },
                        onClick = {
                            onDnsModeChange(mode)
                            expanded = false
                        },
                    )
                }
            }
        }

        Text(
            text = dnsModeDescription(dnsMode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (dnsMode == DnsMode.CUSTOM) {
            OutlinedTextField(
                value = customDnsText,
                onValueChange = {
                    customDnsText = it
                    onCustomDnsChange(it)
                },
                label = { Text("DNS 服务器") },
                placeholder = { Text("例如 223.5.5.5") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        if (dnsMode == DnsMode.DOH || dnsMode == DnsMode.FAKE_IP) {
            OutlinedTextField(
                value = dohUrlText,
                onValueChange = {
                    dohUrlText = it
                    onDohUrlChange(it)
                },
                label = { Text("DoH 地址") },
                placeholder = { Text("https://dns.alidns.com/dns-query") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

private fun dnsModeLabel(mode: DnsMode, short: Boolean = false): String =
    when (mode) {
        DnsMode.FAKE_IP ->
            if (short) "远程解析 (推荐)" else "远程解析 (推荐，防 DNS 污染)"
        DnsMode.DOH ->
            if (short) "加密 DNS (DoH)" else "加密 DNS (DoH)"
        DnsMode.CUSTOM ->
            if (short) "自定义 DNS" else "自定义 DNS"
        DnsMode.SYSTEM ->
            if (short) "系统默认" else "系统默认"
    }

private fun dnsModeDescription(mode: DnsMode): String =
    when (mode) {
        DnsMode.FAKE_IP -> "由代理服务器远程解析域名，可有效避免 DNS 污染，推荐使用。"
        DnsMode.DOH -> "通过加密 DNS 查询解析域名，需填写可访问的 DoH 地址。"
        DnsMode.CUSTOM -> "使用你指定的 DNS 服务器，查询会经代理发出。"
        DnsMode.SYSTEM -> "使用系统默认 DNS，可能受到 DNS 污染影响。"
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
