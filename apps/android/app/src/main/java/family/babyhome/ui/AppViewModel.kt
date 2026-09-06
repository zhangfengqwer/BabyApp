package family.babyhome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.BuildConfig
import family.babyhome.data.settings.ServerConfigStore
import family.babyhome.data.settings.TAILSCALE_BABY_SERVER_URL
import family.babyhome.domain.auth.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AppUiState(
    val connecting: Boolean = true,
    val connected: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val configStore: ServerConfigStore,
) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init { connect() }

    fun connect() = viewModelScope.launch {
        _state.value = AppUiState(connecting = true)
        try {
            val lanServerUrl = configStore.config.first().babyServerUrl.trimEnd('/')
            val candidates = listOf(lanServerUrl, TAILSCALE_BABY_SERVER_URL).distinct()
            var connectedServerUrl: String? = null
            var lastError: Throwable? = null
            for (serverUrl in candidates) {
                val attempt = authRepository.homeLogin(serverUrl, BuildConfig.HOME_ACCESS_KEY)
                if (attempt.isSuccess) {
                    connectedServerUrl = serverUrl
                    break
                }
                lastError = attempt.exceptionOrNull()
            }
            if (connectedServerUrl == null) throw lastError ?: IllegalStateException("No server available")
            configStore.selectBabyServer(connectedServerUrl)
            _state.value = AppUiState(connecting = false, connected = true)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _state.value = AppUiState(
                connecting = false,
                error = "无法连接家庭相册。家中请连接家庭 Wi-Fi；外出请先连接 Tailscale。",
            )
        }
    }
}
