package family.babyhome.ui.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.auth.TokenStore
import family.babyhome.data.immich.ImmichAssetService
import family.babyhome.data.network.BabyApi
import family.babyhome.data.settings.ServerConfigStore
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class GalleryMediaItem(
    val assetId: String,
    val assetType: String,
    val thumbnailUrl: String,
    val previewUrl: String,
    val videoUrl: String,
)

data class MediaGalleryUiState(
    val loading: Boolean = true,
    val items: List<GalleryMediaItem> = emptyList(),
    val initialIndex: Int = 0,
    val accessToken: String? = null,
    val error: String? = null,
)

@HiltViewModel
class MediaGalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val configStore: ServerConfigStore,
    private val api: BabyApi,
    private val assetService: ImmichAssetService,
    private val tokenStore: TokenStore,
) : ViewModel() {
    private val momentId = requireNotNull(savedStateHandle.get<String>("momentId"))
    private val requestedIndex = savedStateHandle.get<String>("initialIndex")?.toIntOrNull() ?: 0
    private val _state = MutableStateFlow(MediaGalleryUiState())
    val state: StateFlow<MediaGalleryUiState> = _state.asStateFlow()

    init {
        require(UUID.fromString(momentId).toString() == momentId)
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val serverUrl = configStore.activeConfig.first().babyServerUrl.trimEnd('/')
            val moment = api.moment("$serverUrl/moments/$momentId").data
            val items = moment.assets.map { asset ->
                GalleryMediaItem(
                    assetId = asset.immichAssetId,
                    assetType = asset.assetType,
                    thumbnailUrl = assetService.thumbnailUrl(serverUrl, asset.immichAssetId),
                    previewUrl = assetService.previewUrl(serverUrl, asset.immichAssetId),
                    videoUrl = assetService.videoUrl(serverUrl, asset.immichAssetId),
                )
            }
            _state.value = MediaGalleryUiState(
                loading = false,
                items = items,
                initialIndex = requestedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                accessToken = tokenStore.accessToken(),
                error = if (items.isEmpty()) "这条动态没有可查看的照片或视频" else null,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _state.value = _state.value.copy(
                loading = false,
                error = "媒体加载失败，请检查家庭服务器连接后重试",
            )
        }
    }
}
