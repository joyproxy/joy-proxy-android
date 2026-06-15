package com.joyproxy.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joyproxy.app.config.DnsMode
import com.joyproxy.app.config.ProxyProtocol
import com.joyproxy.app.config.ProxyScope
import com.joyproxy.app.config.ProxySettings
import com.joyproxy.app.data.SettingsRepository
import com.joyproxy.app.network.ProxyTester
import com.joyproxy.app.vpn.VpnController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProxyTestStatus {
    Idle,
    Testing,
    Success,
    Failed,
}

data class ProxyTestState(
    val status: ProxyTestStatus = ProxyTestStatus.Idle,
    val message: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private var saveJob: Job? = null

    private val _settings = MutableStateFlow(ProxySettings())
    val settings: StateFlow<ProxySettings> = _settings.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _testState = MutableStateFlow(ProxyTestState())
    val testState: StateFlow<ProxyTestState> = _testState.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = repository.settings.first()
        }
    }

    private fun update(block: (ProxySettings) -> ProxySettings) {
        val updated = block(_settings.value)
        _settings.value = updated
        saveJob?.cancel()
        saveJob =
            viewModelScope.launch {
                delay(400)
                repository.save(updated)
            }
    }

    fun setProtocol(protocol: ProxyProtocol) = update { it.copy(protocol = protocol) }
    fun setHost(host: String) = update { it.copy(host = host) }
    fun setPort(port: Int) = update { it.copy(port = port) }
    fun setUsername(username: String) = update { it.copy(username = username) }
    fun setPassword(password: String) = update { it.copy(password = password) }
    fun setScope(scope: ProxyScope) = update { it.copy(scope = scope) }
    fun setSelectedApps(apps: Set<String>) = update { it.copy(selectedApps = apps) }
    fun setDnsMode(mode: DnsMode) = update { it.copy(dnsMode = mode) }
    fun setCustomDns(dns: String) = update { it.copy(customDns = dns) }
    fun setDohUrl(url: String) = update { it.copy(dohUrl = url) }

    fun testProxy() {
        val current = _settings.value
        if (!current.isValid()) {
            _testState.value = ProxyTestState(ProxyTestStatus.Failed, "请先填写有效的代理地址和端口")
            return
        }

        viewModelScope.launch {
            _testState.value = ProxyTestState(ProxyTestStatus.Testing, "正在测试代理连通性…")
            val result = ProxyTester.test(current)
            _testState.value =
                ProxyTestState(
                    status = if (result.success) ProxyTestStatus.Success else ProxyTestStatus.Failed,
                    message = result.message,
                )
        }
    }

    fun connect(onNeedPermission: () -> Unit) {
        val current = _settings.value
        if (!current.isValid()) {
            _message.value = "请填写有效的代理地址和端口"
            return
        }
        if (current.scope != ProxyScope.GLOBAL && current.selectedApps.isEmpty()) {
            _message.value = "请选择至少一个应用"
            return
        }
        onNeedPermission()
    }

    fun startVpn() {
        val current = _settings.value
        VpnController.start(getApplication(), current)
        viewModelScope.launch { repository.setConnected(true) }
        _settings.value = current.copy(connected = true)
    }

    fun disconnect() {
        VpnController.stop(getApplication())
        viewModelScope.launch { repository.setConnected(false) }
        _settings.value = _settings.value.copy(connected = false)
    }

    fun clearMessage() {
        _message.value = null
    }
}
