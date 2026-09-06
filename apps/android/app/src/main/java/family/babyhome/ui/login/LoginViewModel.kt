package family.babyhome.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.settings.ServerConfig
import family.babyhome.data.settings.DEFAULT_HOME_USERNAME
import family.babyhome.domain.auth.AuthRepository
import family.babyhome.domain.settings.ConnectionTestResult
import family.babyhome.domain.settings.ServerSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = DEFAULT_HOME_USERNAME,
    val password: String = "",
    val loading: Boolean = false,
    val message: String? = null,
    val loginSucceeded: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: ServerSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(serverUrl = settings.config.first().babyServerUrl) }
        }
    }

    fun password(value: String) = _state.update { it.copy(password = value, message = null) }

    fun testConnection() = viewModelScope.launch {
        _state.update { it.copy(loading = true, message = null) }
        val message = when (val result = settings.testConnection(_state.value.serverUrl)) {
            is ConnectionTestResult.Success -> "连接成功 · ${result.latencyMs}ms"
            is ConnectionTestResult.Error -> result.message
        }
        _state.update { it.copy(loading = false, message = message) }
    }

    fun login() = viewModelScope.launch {
        val current = _state.value
        if (current.password.isBlank()) {
            _state.update { it.copy(message = "请输入家庭相册密码") }
            return@launch
        }
        _state.update { it.copy(loading = true, message = null) }
        runCatching {
            val old = settings.config.first()
            settings.save(ServerConfig(current.serverUrl, old.immichUrl))
            auth.login(current.serverUrl, current.username, current.password).getOrThrow()
        }.onSuccess {
            _state.update { it.copy(loading = false, loginSucceeded = true) }
        }.onFailure { error ->
            val message = if (error is HttpException && error.code() == 401) {
                "密码错误"
            } else {
                "无法连接家庭服务器，请确认手机和电脑连接同一个家庭网络"
            }
            _state.update { it.copy(loading = false, message = message) }
        }
    }
}
