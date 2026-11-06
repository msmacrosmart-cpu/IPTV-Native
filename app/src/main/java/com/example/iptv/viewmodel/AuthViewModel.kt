package com.example.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.AccountInfo
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.repository.XtreamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** High level session state consumed by MainActivity. */
sealed interface SessionState {
    /** Reading the stored session / validating it against the panel. */
    data object Restoring : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val account: AccountInfo, val offline: Boolean = false) : SessionState
}

class AuthViewModel(private val repository: XtreamRepository) : ViewModel() {

    private val _session = MutableStateFlow<SessionState>(SessionState.Restoring)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    val serverInput = MutableStateFlow("")
    val usernameInput = MutableStateFlow("")
    val passwordInput = MutableStateFlow("")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage.asStateFlow()

    init {
        repository.currentCredentials()?.let { credentials ->
            serverInput.value = credentials.serverUrl
            usernameInput.value = credentials.username
            passwordInput.value = credentials.password
        }
        restoreSession()
    }

    fun updateServer(value: String) {
        serverInput.value = value
        _errorMessage.value = null
    }

    fun updateUsername(value: String) {
        usernameInput.value = value
        _errorMessage.value = null
    }

    fun updatePassword(value: String) {
        passwordInput.value = value
        _errorMessage.value = null
    }

    /**
     * Restores a saved session immediately (from the encrypted preferences) and validates it
     * in the background, so the app never opens on a blank screen while the network answers.
     */
    private fun restoreSession() {
        if (!repository.hasStoredSession()) {
            _session.value = SessionState.LoggedOut
            return
        }

        val cached = repository.cachedAccount()
        if (cached != null) {
            _session.value = SessionState.LoggedIn(cached, offline = true)
        }

        viewModelScope.launch {
            when (val result = repository.restoreSession()) {
                is ApiResult.Success -> {
                    _warningMessage.value = null
                    _session.value = SessionState.LoggedIn(result.data, offline = false)
                }

                is ApiResult.Failure -> {
                    if (cached != null) {
                        _warningMessage.value = "Não foi possível validar a conta agora: ${result.message}"
                    } else {
                        _errorMessage.value = result.message
                        _session.value = SessionState.LoggedOut
                    }
                }
            }
        }
    }

    fun login() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.login(
                rawServer = serverInput.value,
                rawUsername = usernameInput.value,
                rawPassword = passwordInput.value
            )
            _isLoading.value = false
            when (result) {
                is ApiResult.Success -> {
                    _warningMessage.value = null
                    _session.value = SessionState.LoggedIn(result.data, offline = false)
                }

                is ApiResult.Failure -> _errorMessage.value = result.message
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun dismissWarning() {
        _warningMessage.value = null
    }

    fun logout() {
        repository.logout()
        serverInput.value = ""
        usernameInput.value = ""
        passwordInput.value = ""
        _errorMessage.value = null
        _warningMessage.value = null
        _session.value = SessionState.LoggedOut
    }

    /** Factory used with `viewModel(factory = ...)` from Compose. */
    class Factory(private val repository: XtreamRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                return AuthViewModel(repository) as T
            }
            throw IllegalArgumentException("ViewModel desconhecido: ${modelClass.name}")
        }
    }
}
