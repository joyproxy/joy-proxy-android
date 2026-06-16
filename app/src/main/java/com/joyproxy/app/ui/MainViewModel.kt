package com.joyproxy.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joyproxy.app.config.DnsMode
import com.joyproxy.app.config.ProxyProtocol
import com.joyproxy.app.config.ProxyScope
import com.joyproxy.app.config.ProxySettings
import com.joyproxy.app.config.SavedProxy
import com.joyproxy.app.data.ProxyHistoryRepository
import com.joyproxy.app.data.SettingsRepository
import com.joyproxy.app.network.ProxyTester
import com.joyproxy.app.vpn.VpnController
import com.joyproxy.app.vpn.VpnStatusBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val historyRepository = ProxyHistoryRepository(application)
    private var saveJob: Job? = null
    private var historyJob: Job? = null

    private val _settings = MutableStateFlow(ProxySettings())
    val settings: StateFlow<ProxySettings> = _settings.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _testState = MutableStateFlow(ProxyTestState())
    val testState: StateFlow<ProxyTestState> = _testState.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _savedProxies = MutableStateFlow<List<SavedProxy>>(emptyList())
    val savedProxies: StateFlow<List<SavedProxy>> = _savedProxies.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.history.collect { _savedProxies.value = it }
        }
        viewModelScope.launch {
            val saved = repository.settings.first()
            _settings.value = saved.copy(connected = false)
            if (saved.connected) {
                repository.setConnected(false)
            }
        }
        viewModelScope.launch {
            VpnStatusBus.connected.collect { connected ->
                _settings.value = _settings.value.copy(connected = connected)
            }
        }
        viewModelScope.launch {
            VpnStatusBus.events.collect { event ->
                when (event) {
                    is VpnStatusBus.Event.Connecting -> _connecting.value = true
                    is VpnStatusBus.Event.Connected,
                    is VpnStatusBus.Event.Disconnected,
                    -> _connecting.value = false
                    is VpnStatusBus.Event.Failed -> {
                        _connecting.value = false
                        _message.value = event.message
                    }
                }
            }
        }
    }

    private fun update(block: (ProxySettings) -> ProxySettings) {
        val updated = block(_settings.value)
        _settings.value = updated
        saveJob?.cancel()
        saveJob =
            viewModelScope.launch {
                delay(400)
                repository.save(updated.copy(connected = _settings.value.connected))
            }
        scheduleHistorySave()
    }

    private fun scheduleHistorySave() {
        historyJob?.cancel()
        historyJob =
            viewModelScope.launch {
                delay(1200)
                val current = _settings.value
                if (current.isValid()) {
                    historyRepository.upsert(current)
                }
            }
    }

    private fun saveHistoryNow() {
        historyJob?.cancel()
        viewModelScope.launch {
            val current = _settings.value
            if (current.isValid()) {
                historyRepository.upsert(current)
            }
        }
    }

    suspend fun flushSettings() {
        saveJob?.cancel()
        repository.save(_settings.value.copy(connected = VpnStatusBus.connected.value))
    }

    fun setProtocol(protocol: ProxyProtocol) = update { it.copy(protocol = protocol) }
    fun setHost(host: String) = update { it.copy(host = host) }
    fun setPort(port: Int) = update { it.copy(port = port) }
    fun setUsername(username: String) = update { it.copy(username = username) }
    fun setPassword(password: String) = update { it.copy(password = password) }
    fun setScope(scope: ProxyScope) {
        update { it.copy(scope = scope) }
        notifyReconnectIfConnected("代理范围")
    }

    fun setSelectedApps(apps: Set<String>) {
        update { it.copy(selectedApps = apps) }
        notifyReconnectIfConnected("应用列表")
    }

    fun setDnsMode(mode: DnsMode) {
        update { it.copy(dnsMode = mode) }
        notifyReconnectIfConnected("DNS 模式")
    }

    fun setCustomDns(dns: String) = update { it.copy(customDns = dns) }

    fun setDohUrl(url: String) = update { it.copy(dohUrl = url) }

    fun applySavedProxy(proxy: SavedProxy) {
        update {
            it.copy(
                protocol = proxy.protocol,
                host = proxy.host,
                port = proxy.port,
                username = proxy.username,
                password = proxy.password,
            )
        }
    }

    fun deleteSavedProxy(id: String) {
        viewModelScope.launch {
            historyRepository.delete(id)
        }
    }

    private fun notifyReconnectIfConnected(settingName: String) {
        if (_settings.value.connected) {
            _message.value = "已修改$settingName，需断开后重新连接方可生效"
        }
    }

    fun testProxy() {
        val current = _settings.value
        if (current.connected || _connecting.value) {
            _testState.value = ProxyTestState(ProxyTestStatus.Failed, "已连接时无法测试，请先断开连接")
            return
        }
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
            if (result.success) {
                saveHistoryNow()
            }
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
        saveHistoryNow()
        onNeedPermission()
    }

    fun startVpn() {
        val current = _settings.value
        VpnController.start(getApplication(), current)
    }

    fun disconnect() {
        VpnController.stop(getApplication())
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(message: String) {
        _message.value = message
    }
}
