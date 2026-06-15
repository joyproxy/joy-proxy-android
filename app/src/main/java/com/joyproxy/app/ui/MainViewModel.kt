package com.joyproxy.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joyproxy.app.config.DnsMode
import com.joyproxy.app.config.ProxyProtocol
import com.joyproxy.app.config.ProxyScope
import com.joyproxy.app.config.ProxySettings
import com.joyproxy.app.data.SettingsRepository
import com.joyproxy.app.vpn.VpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val settings: StateFlow<ProxySettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ProxySettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun update(block: (ProxySettings) -> ProxySettings) {
        viewModelScope.launch {
            repository.save(block(settings.value))
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

    fun connect(onNeedPermission: () -> Unit) {
        val current = settings.value
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
        val current = settings.value
        VpnController.start(getApplication(), current)
        viewModelScope.launch { repository.setConnected(true) }
    }

    fun disconnect() {
        VpnController.stop(getApplication())
        viewModelScope.launch { repository.setConnected(false) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
