package com.joyproxy.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyproxy.app.config.DnsMode
import com.joyproxy.app.config.ProxyProtocol
import com.joyproxy.app.config.ProxyScope
import com.joyproxy.app.config.ProxySettings
import com.joyproxy.app.config.SavedProxy
import com.joyproxy.app.ui.theme.JoyProxyColors
import com.joyproxy.app.ui.theme.JoyProxyHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onConnect: () -> Unit,
    onPickApps: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val connecting by viewModel.connecting.collectAsState()
    val message by viewModel.message.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val savedProxies by viewModel.savedProxies.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var dnsTapCount by remember { mutableStateOf(0) }
    var showDnsSettings by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = JoyProxyColors.Surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            JoyProxyHeader(
                onTitleClick = {
                    if (!showDnsSettings) {
                        dnsTapCount++
                        if (dnsTapCount >= 7) {
                            showDnsSettings = true
                        }
                    }
                },
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            ConnectionCard(
                connected = settings.connected,
                connecting = connecting,
                onConnect = { viewModel.connect(onConnect) },
                onDisconnect = viewModel::disconnect,
            )

            ProxyConfigCard(
                settings = settings,
                testState = testState,
                savedProxies = savedProxies,
                connected = settings.connected,
                connecting = connecting,
                onProtocolChange = viewModel::setProtocol,
                onHostChange = viewModel::setHost,
                onPortChange = viewModel::setPort,
                onUsernameChange = viewModel::setUsername,
                onPasswordChange = viewModel::setPassword,
                onApplySavedProxy = viewModel::applySavedProxy,
                onDeleteSavedProxy = viewModel::deleteSavedProxy,
                onTest = viewModel::testProxy,
            )

            ScopeCard(
                scope = settings.scope,
                selectedCount = settings.selectedApps.size,
                onScopeChange = viewModel::setScope,
                onPickApps = onPickApps,
            )

            if (showDnsSettings) {
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
    }
}

@Composable
private fun ConnectionCard(
    connected: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val statusColor =
        when {
            connected -> JoyProxyColors.Success
            connecting -> JoyProxyColors.Warning
            else -> JoyProxyColors.TextSecondary
        }
    val statusText =
        when {
            connected -> "已连接"
            connecting -> "正在连接…"
            else -> "未连接"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JoyProxyColors.SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                connected -> Brush.linearGradient(listOf(JoyProxyColors.Error, Color(0xFFDC2626)))
                                connecting -> Brush.linearGradient(listOf(JoyProxyColors.TextSecondary, JoyProxyColors.TextSecondary))
                                else -> JoyProxyColors.BrandGradient
                            },
                        )
                        .clickable(enabled = !connecting) {
                            if (connected) onDisconnect() else onConnect()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        when {
                            connected -> "断开连接"
                            connecting -> "连接中…"
                            else -> "连接代理"
                        },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyConfigCard(
    settings: ProxySettings,
    testState: ProxyTestState,
    savedProxies: List<SavedProxy>,
    connected: Boolean,
    connecting: Boolean,
    onProtocolChange: (ProxyProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onApplySavedProxy: (SavedProxy) -> Unit,
    onDeleteSavedProxy: (String) -> Unit,
    onTest: () -> Unit,
) {
    var host by remember { mutableStateOf(settings.host) }
    var portText by remember { mutableStateOf(settings.port.toString()) }
    var username by remember { mutableStateOf(settings.username) }
    var password by remember { mutableStateOf(settings.password) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(settings.host) { if (host != settings.host) host = settings.host }
    LaunchedEffect(settings.port) {
        val portString = settings.port.toString()
        if (portText != portString) portText = portString
    }
    LaunchedEffect(settings.username) { if (username != settings.username) username = settings.username }
    LaunchedEffect(settings.password) { if (password != settings.password) password = settings.password }

    SectionCard(title = "代理服务器") {
        if (savedProxies.isNotEmpty()) {
            var historyExpanded by remember { mutableStateOf(false) }
            val currentId =
                if (settings.isValid()) {
                    SavedProxy.fingerprint(
                        settings.protocol,
                        settings.host.trim(),
                        settings.port,
                        settings.username.trim(),
                    )
                } else {
                    null
                }
            val historyLabel =
                savedProxies.find { it.id == currentId }?.displayLabel()
                    ?: "选择已保存的代理 (${savedProxies.size})"

            ExposedDropdownMenuBox(
                expanded = historyExpanded,
                onExpandedChange = { historyExpanded = it },
            ) {
                OutlinedTextField(
                    value = historyLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("历史记录") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = historyExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                ExposedDropdownMenu(
                    expanded = historyExpanded,
                    onDismissRequest = { historyExpanded = false },
                ) {
                    savedProxies.forEach { proxy ->
                        DropdownMenuItem(
                            text = { Text(proxy.displayLabel()) },
                            onClick = {
                                onApplySavedProxy(proxy)
                                historyExpanded = false
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onDeleteSavedProxy(proxy.id) },
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        var protocolExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = protocolExpanded, onExpandedChange = { protocolExpanded = it }) {
            OutlinedTextField(
                value = settings.protocol.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("协议") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
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
            onValueChange = { input ->
                val parsed = parseHostPort(input)
                if (parsed != null) {
                    host = parsed.first
                    portText = parsed.second.toString()
                    onHostChange(parsed.first)
                    onPortChange(parsed.second)
                } else {
                    host = input
                    onHostChange(input)
                }
            },
            label = { Text("地址 (IP 或域名)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
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
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
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
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
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
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
        )

        OutlinedButton(
            onClick = onTest,
            enabled = !connected && !connecting && testState.status != ProxyTestStatus.Testing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, JoyProxyColors.Indigo),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = JoyProxyColors.Indigo),
        ) {
            if (testState.status == ProxyTestStatus.Testing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                when {
                    connected -> "已连接，请先断开再测试"
                    testState.status == ProxyTestStatus.Testing -> "测试中…"
                    else -> "测试代理连通性"
                },
            )
        }

        if (testState.status == ProxyTestStatus.Success || testState.status == ProxyTestStatus.Failed) {
            Text(
                text = testState.message,
                color = if (testState.status == ProxyTestStatus.Success) JoyProxyColors.Success else JoyProxyColors.Error,
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
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
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
            Button(
                onClick = onPickApps,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JoyProxyColors.Indigo),
            ) {
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
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(
                    DnsMode.FAKE_IP,
                    DnsMode.DOH,
                    DnsMode.CUSTOM,
                    DnsMode.SYSTEM,
                ).forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(dnsModeLabel(mode)) },
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
            color = JoyProxyColors.TextSecondary,
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
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
            )
        }

        if (dnsMode == DnsMode.FAKE_IP) {
            OutlinedTextField(
                value = dohUrlText,
                onValueChange = {
                    dohUrlText = it
                    onDohUrlChange(it)
                },
                label = { Text("远程 DNS (DoH)") },
                placeholder = { Text("https://dns.alidns.com/dns-query") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
            )
        }

        if (dnsMode == DnsMode.DOH) {
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
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
            )
        }
    }
}

private fun dnsModeLabel(mode: DnsMode): String =
    when (mode) {
        DnsMode.FAKE_IP -> "推荐"
        DnsMode.DOH -> "加密 DNS (DoH)"
        DnsMode.CUSTOM -> "自定义 DNS"
        DnsMode.SYSTEM -> "系统默认"
    }

private fun dnsModeDescription(mode: DnsMode): String =
    when (mode) {
        DnsMode.FAKE_IP -> "应用拿到虚拟 IP，真实域名在代理端解析。下方 DoH 用于代理端远程解析。"
        DnsMode.DOH -> "DNS 查询经 HTTPS 加密后走代理，返回真实 IP。不经过虚拟 IP 映射。"
        DnsMode.CUSTOM -> "使用你指定的 DNS 服务器，查询会经代理发出。"
        DnsMode.SYSTEM -> "使用系统默认 DNS。"
    }

/** 解析 host:port 或 [ipv6]:port 格式，用于粘贴时自动拆分。 */
private fun parseHostPort(input: String): Pair<String, Int>? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    val ipv6 = Regex("""^\[(.+)]:(\d+)$""").find(trimmed)
    if (ipv6 != null) {
        val port = ipv6.groupValues[2].toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return ipv6.groupValues[1] to port
    }

    val colonIndex = trimmed.lastIndexOf(':')
    if (colonIndex <= 0) return null

    val hostPart = trimmed.substring(0, colonIndex)
    val portPart = trimmed.substring(colonIndex + 1)
    if (hostPart.isBlank() || portPart.isBlank()) return null

    val port = portPart.toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    if (!portPart.all { it.isDigit() }) return null

    return hostPart to port
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, JoyProxyColors.Border, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JoyProxyColors.SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(JoyProxyColors.BrandGradient),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            content()
        }
    }
}

private val fieldColors
    @Composable get() =
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = JoyProxyColors.Indigo,
            unfocusedBorderColor = JoyProxyColors.Border,
            focusedLabelColor = JoyProxyColors.Indigo,
            cursorColor = JoyProxyColors.Indigo,
        )
