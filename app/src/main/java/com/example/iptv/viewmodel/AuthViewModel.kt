package com.example.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.repository.XtreamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: XtreamRepository) : ViewModel() {

    val serverUrl = MutableStateFlow(repository.currentServer)
    val username = MutableStateFlow(repository.currentUser)
    val password = MutableStateFlow(repository.currentPass)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    fun updateServer(value: String) {
        serverUrl.value = value
        _errorMessage.value = null
    }

    fun updateUsername(value: String) {
        username.value = value
        _errorMessage.value = null
    }

    fun updatePassword(value: String) {
        password.value = value
        _errorMessage.value = null
    }

    fun login(onSuccess: () -> Unit) {
        val server = serverUrl.value.trim()
        val user = username.value.trim()
        val pass = password.value.trim()

        if (server.isEmpty()) {
            _errorMessage.value = "Por favor, informe a URL do servidor Xtream."
            return
        }
        if (user.isEmpty()) {
            _errorMessage.value = "Por favor, informe o nome de usuário."
            return
        }
        if (pass.isEmpty()) {
            _errorMessage.value = "Por favor, informe a senha de acesso."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = repository.login(server, user, pass)
            _isLoading.value = false

            result.onSuccess {
                _loginSuccess.value = true
                onSuccess()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Erro ao autenticar no servidor Xtream."
            }
        }
    }
}
