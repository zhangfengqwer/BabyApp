package family.babyhome.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.settings.ServerConfig
import family.babyhome.domain.settings.ConnectionTestResult
import family.babyhome.domain.settings.ServerSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerSettingsUiState(
    val babyServerUrl: String = "",
    val immichUrl: String = "",
    val isTesting: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val repository: ServerSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServerSettingsUiState())
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = repository.config.first()
            _uiState.update {
                it.copy(babyServerUrl = config.babyServerUrl, immichUrl = config.immichUrl)
            }
        }
    }

    fun onBabyServerUrlChange(value: String) = _uiState.update {
        it.copy(babyServerUrl = value, message = null)
    }

    fun onImmichUrlChange(value: String) = _uiState.update {
        it.copy(immichUrl = value, message = null)
    }

    fun save() {
        viewModelScope.launch {
            try {
                val current = _uiState.value
                repository.save(ServerConfig(current.babyServerUrl, current.immichUrl))
                _uiState.update { it.copy(message = "服务器设置已保存", messageIsError = false) }
            } catch (_: IllegalArgumentException) {
                _uiState.update {
                    it.copy(message = "地址格式不正确，请检查后重试", messageIsError = true)
                }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, message = null) }
            when (val result = repository.testConnection(_uiState.value.babyServerUrl)) {
                is ConnectionTestResult.Success -> _uiState.update {
                    it.copy(
                        isTesting = false,
                        messageIsError = false,
                        message = "连接成功 · ${result.latencyMs}ms · 数据库 ${statusLabel(result.databaseStatus)} · Immich ${statusLabel(result.immichStatus)}",
                    )
                }
                is ConnectionTestResult.Error -> _uiState.update {
                    it.copy(isTesting = false, messageIsError = true, message = result.message)
                }
            }
        }
    }

    private fun statusLabel(status: String) = when (status) {
        "up" -> "正常"
        "not_configured" -> "未配置"
        else -> "异常"
    }
}

