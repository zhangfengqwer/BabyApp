package family.babyhome.ui.publish

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.immich.ImmichRepository
import family.babyhome.data.immich.LocalMedia
import family.babyhome.data.network.CreateMomentAssetRequest
import family.babyhome.domain.publish.PublishRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import java.time.LocalDate
import java.time.ZoneId

enum class UploadStatus { WAITING, UPLOADING, UPLOADED, FAILED }

data class PublishMediaItem(
    val media: LocalMedia,
    val status: UploadStatus = UploadStatus.WAITING,
    val progress: Float = 0f,
    val assetId: String? = null,
)

data class PublishUiState(
    val eventDate: String = LocalDate.now().toString(),
    val content: String = "",
    val location: String = "",
    val items: List<PublishMediaItem> = emptyList(),
    val publishing: Boolean = false,
    val message: String? = null,
    val published: Boolean = false,
)

@HiltViewModel
class PublishViewModel @Inject constructor(
    private val immich: ImmichRepository,
    private val publishRepository: PublishRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PublishUiState())
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    fun content(value: String) = _state.update { it.copy(content = value, message = null) }
    fun location(value: String) = _state.update { it.copy(location = value, message = null) }
    fun eventDate(value: String) = _state.update { it.copy(eventDate = value, message = null) }

    fun addUris(uris: List<Uri>) {
        val existing = _state.value.items.map { it.media.uri }.toSet()
        val additions = uris.filterNot(existing::contains).map { PublishMediaItem(immich.describe(it)) }
        val capturedDate = additions.firstNotNullOfOrNull { item ->
            item.media.capturedAt?.atZone(ZoneId.systemDefault())?.toLocalDate()
        }
        // 相册里的同一天可能有很多张照片；不要在这里截断用户选择的媒体。
        _state.update {
            it.copy(
                items = it.items + additions,
                eventDate = capturedDate?.toString() ?: it.eventDate,
                message = if (capturedDate != null) "已按照片拍摄时间选择日期" else null,
            )
        }
    }

    fun remove(uri: Uri) = _state.update { state ->
        if (state.publishing) state else state.copy(items = state.items.filterNot { it.media.uri == uri })
    }

    fun publish() = viewModelScope.launch {
        val initial = _state.value
        if (initial.publishing) return@launch
        val eventInstant = runCatching {
            val date = LocalDate.parse(initial.eventDate)
            require(!date.isAfter(LocalDate.now()))
            date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        }.getOrElse {
            _state.update { it.copy(message = "请选择正确的事件日期，不能晚于今天") }
            return@launch
        }
        if (initial.content.isBlank() && initial.items.isEmpty()) {
            _state.update { it.copy(message = "请写点文字或选择照片/视频") }
            return@launch
        }
        _state.update { it.copy(publishing = true, message = null) }
        for (index in _state.value.items.indices) {
            if (_state.value.items[index].assetId != null) continue
            updateItem(index) { it.copy(status = UploadStatus.UPLOADING, progress = 0f) }
            val result = withContext(Dispatchers.IO) {
                immich.upload(_state.value.items[index].media) { progress ->
                    updateItem(index) { it.copy(progress = progress) }
                }
            }
            result.onSuccess { response ->
                updateItem(index) { it.copy(status = UploadStatus.UPLOADED, progress = 1f, assetId = response.id) }
            }.onFailure { error ->
                updateItem(index) { it.copy(status = UploadStatus.FAILED) }
                if (error is HttpException && error.code() == 401) {
                    _state.update { it.copy(message = "登录已过期，请重新打开 App 登录") }
                }
            }
        }
        val current = _state.value
        val failed = current.items.count { it.assetId == null }
        if (failed > 0) {
            _state.update {
                it.copy(
                    publishing = false,
                    message = current.message
                        ?: "$failed 个文件上传失败，已成功的文件不会重复上传，请点击重试",
                )
            }
            return@launch
        }
        val assets = current.items.mapIndexed { index, item ->
            CreateMomentAssetRequest(
                immichAssetId = requireNotNull(item.assetId),
                assetType = if (item.media.mimeType.startsWith("video/")) "VIDEO" else "IMAGE",
                sortOrder = index,
            )
        }
        publishRepository.createMoment(current.content, current.location, assets, eventInstant)
            .onSuccess { _state.update { it.copy(publishing = false, published = true, message = "发布成功") } }
            .onFailure { _state.update { it.copy(publishing = false, message = "媒体已上传，但动态创建失败；请点击重试，不会重复上传") } }
    }

    private fun updateItem(index: Int, transform: (PublishMediaItem) -> PublishMediaItem) {
        _state.update { state ->
            state.copy(items = state.items.mapIndexed { itemIndex, item -> if (itemIndex == index) transform(item) else item })
        }
    }
}
