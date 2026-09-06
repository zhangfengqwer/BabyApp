package family.babyhome.ui.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.auth.TokenStore
import family.babyhome.data.immich.ImmichAssetService
import family.babyhome.data.settings.ServerConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MediaUiState(
    val thumbnailUrl: String? = null,
    val previewUrl: String? = null,
    val videoUrl: String? = null,
    val accessToken: String? = null,
)

@HiltViewModel
class MediaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    configStore: ServerConfigStore,
    assetService: ImmichAssetService,
    tokenStore: TokenStore,
) : ViewModel() {
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    init {
        val assetId = requireNotNull(savedStateHandle.get<String>("assetId"))
        viewModelScope.launch {
            val serverUrl = configStore.activeConfig.first().babyServerUrl
            _state.value = MediaUiState(
                thumbnailUrl = assetService.thumbnailUrl(serverUrl, assetId),
                previewUrl = assetService.previewUrl(serverUrl, assetId),
                videoUrl = assetService.videoUrl(serverUrl, assetId),
                accessToken = tokenStore.accessToken(),
            )
        }
    }
}
