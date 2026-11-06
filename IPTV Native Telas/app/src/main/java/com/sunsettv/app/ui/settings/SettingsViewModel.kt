package com.sunsettv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunsettv.app.core.AppPrefs
import com.sunsettv.app.core.ServiceLocator
import com.sunsettv.app.core.SessionStore
import com.sunsettv.app.data.EpgRepository
import com.sunsettv.app.data.EpgUpdateState
import com.sunsettv.app.data.XtreamClient
import com.sunsettv.app.domain.AccountInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AccountState {
    object Loading : AccountState
    data class Error(val message: String) : AccountState
    data class Ready(val info: AccountInfo) : AccountState
}

data class SettingsUiState(
    val account: AccountState = AccountState.Loading,
    val customXmltv: String = "",
    val autoEpg: Boolean = true,
    val lastEpgUpdateMs: Long = 0L,
    val epgChannels: Int = 0,
    val epgPrograms: Int = 0,
)

class SettingsViewModel(
    private val client: XtreamClient,
    private val session: SessionStore,
    private val epg: EpgRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(customXmltv = prefs.customXmltvUrl, autoEpg = prefs.autoUpdateEpg)
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    val epgState: StateFlow<EpgUpdateState> = epg.state

    init {
        loadAccount()
        viewModelScope.launch {
            epg.version.collect {
                _state.update { s ->
                    s.copy(
                        lastEpgUpdateMs = prefs.lastEpgUpdateMs,
                        epgChannels = prefs.epgChannels,
                        epgPrograms = prefs.epgPrograms,
                    )
                }
            }
        }
        viewModelScope.launch { epg.loadFromDisk() }
    }

    fun loadAccount() {
        viewModelScope.launch {
            _state.update { it.copy(account = AccountState.Loading) }
            val c = session.get()
            if (c == null) {
                _state.update { it.copy(account = AccountState.Error("Sessão não encontrada. Faça login novamente.")) }
                return@launch
            }
            try {
                val info = client.getAccountInfo(c)
                _state.update { it.copy(account = AccountState.Ready(info)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(account = AccountState.Error(e.message ?: "Falha ao carregar a conta")) }
            }
        }
    }

    fun updateEpg() {
        val c = session.get() ?: return
        epg.refreshAsync(c)
    }

    fun setCustomXmltv(url: String) {
        prefs.customXmltvUrl = url
        _state.update { it.copy(customXmltv = url) }
    }

    fun setAutoEpg(enabled: Boolean) {
        prefs.autoUpdateEpg = enabled
        _state.update { it.copy(autoEpg = enabled) }
    }

    fun logout(onDone: () -> Unit) {
        session.clear()
        onDone()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel(ServiceLocator.client, ServiceLocator.session, ServiceLocator.epg, ServiceLocator.prefs)
            }
        }
    }
}
