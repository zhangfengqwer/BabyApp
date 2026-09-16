package family.babyhome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.BuildConfig
import family.babyhome.data.settings.ServerConfigStore
import family.babyhome.data.settings.TAILSCALE_BABY_SERVER_URL
import family.babyhome.domain.auth.AuthRepository
import family.babyhome.update.AppRelease
import family.babyhome.update.AppUpdateManager
import family.babyhome.data.family.FamilyRepository
import family.babyhome.data.network.FamilyUser
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val connecting: Boolean = true,
    val connected: Boolean = false,
    val error: String? = null,
    val update: AppRelease? = null,
    val updating: Boolean = false,
    val updateError: String? = null,
    val selectingIdentity: Boolean = false,
    val members: List<FamilyUser> = emptyList(),
    val identity: FamilyUser? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val configStore: ServerConfigStore,
    private val updateManager: AppUpdateManager,
    private val family: FamilyRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init { connect() }

    fun connect() = viewModelScope.launch {
        _state.value = AppUiState(connecting = true)
        try {
            val username = configStore.identity.first()
            val lanServerUrl = configStore.config.first().babyServerUrl.trimEnd('/')
            val candidates = listOf(lanServerUrl, TAILSCALE_BABY_SERVER_URL).distinct()
            var connectedServerUrl: String? = null
            var lastError: Throwable? = null
            for (serverUrl in candidates) {
                val attempt = authRepository.homeLogin(serverUrl, BuildConfig.HOME_ACCESS_KEY, username)
                if (attempt.isSuccess) {
                    connectedServerUrl = serverUrl
                    break
                }
                lastError = attempt.exceptionOrNull()
            }
            if (connectedServerUrl == null) throw lastError ?: IllegalStateException("No server available")
            configStore.selectBabyServer(connectedServerUrl)
            val members = family.load().members
            _state.value = AppUiState(connecting = false, connected = username != null, selectingIdentity = username == null, members = members, identity = members.firstOrNull { it.username == username })
            checkForUpdate(connectedServerUrl)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _state.value = AppUiState(
                connecting = false,
                error = "无法连接家庭相册。家中请连接家庭 Wi-Fi；外出请先连接 Tailscale。",
            )
        }
    }
    fun selectIdentity(username: String) = viewModelScope.launch { configStore.saveIdentity(username); connect() }
    fun switchIdentity() = viewModelScope.launch { configStore.saveIdentity(null); authRepository.logout(); connect() }

    fun dismissUpdate() = _state.update { it.copy(update = null, updateError = null) }

    fun installUpdate() = viewModelScope.launch {
        val release = _state.value.update ?: return@launch
        val serverUrl = configStore.activeConfig.first().babyServerUrl
        _state.update { it.copy(updating = true, updateError = null) }
        updateManager.downloadAndInstall(serverUrl, release)
            .onFailure { error ->
                _state.update {
                    it.copy(
                        updating = false,
                        updateError = error.message ?: "更新下载失败，请检查网络后重试",
                    )
                }
            }
            .onSuccess { _state.update { it.copy(updating = false) } }
    }

    private suspend fun checkForUpdate(serverUrl: String) {
        updateManager.check(serverUrl).onSuccess { release ->
            if (release != null) _state.update { it.copy(update = release) }
        }
    }
}
