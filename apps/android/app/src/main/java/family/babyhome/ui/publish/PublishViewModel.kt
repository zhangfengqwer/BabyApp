package family.babyhome.ui.publish

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.immich.ImmichRepository
import family.babyhome.data.immich.LocalMedia
import family.babyhome.data.network.CreateMomentAssetRequest
import family.babyhome.domain.publish.PublishRepository
import family.babyhome.domain.publish.PublishDatePlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    val automaticDates: Boolean = true,
    val readingMedia: Boolean = false,
    val completedDates: Set<String> = emptySet(),
    val publishedDate: String? = null,
)

@HiltViewModel
class PublishViewModel @Inject constructor(
    private val immich: ImmichRepository,
    private val publishRepository: PublishRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PublishUiState())
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    private fun editable() = !_state.value.publishing && _state.value.completedDates.isEmpty()
    fun content(value: String) { if (editable()) _state.update { it.copy(content = value, message = null) } }
    fun location(value: String) { if (editable()) _state.update { it.copy(location = value, message = null) } }
    fun eventDate(value: String) { if (editable()) _state.update { it.copy(eventDate = value, message = null) } }
    fun automaticDates(value: Boolean) { if (editable()) _state.update { it.copy(automaticDates = value) } }

    fun addUris(uris: List<Uri>) = viewModelScope.launch {
        if (!editable() || _state.value.readingMedia) return@launch
        _state.update { it.copy(readingMedia = true) }
        try {
        val existing = _state.value.items.map { it.media.uri }.toSet()
        val additions = withContext(Dispatchers.IO) {
            uris.distinct().filterNot(existing::contains).map { PublishMediaItem(immich.describe(it)) }
        }
        // 相册里的同一天可能有很多张照片；不要在这里截断用户选择的媒体。
        _state.update {
            it.copy(
                items = it.items + additions,
                message = "已读取媒体时间，拍摄时间不明的文件使用手选日期",
            )
        }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _state.update { it.copy(message = "无法读取所选媒体，请重新选择") }
        } finally {
            _state.update { it.copy(readingMedia = false) }
        }
    }

    fun remove(uri: Uri) = _state.update { state ->
        if (state.publishing || state.completedDates.isNotEmpty()) state else state.copy(items = state.items.filterNot { it.media.uri == uri })
    }

    fun publish() = viewModelScope.launch {
        val initial = _state.value
        if (initial.publishing || initial.readingMedia) return@launch
        val grouped = initial.items.groupBy { PublishDatePlanner.date(it.media.capturedAt, initial.eventDate, initial.automaticDates) }
        val dates = grouped.keys.ifEmpty { setOf(initial.eventDate) }
        runCatching { PublishDatePlanner.validate(dates) }.getOrElse {
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
        val groups = current.items.groupBy { PublishDatePlanner.date(it.media.capturedAt, current.eventDate, current.automaticDates) }
        for (date in dates.sorted()) {
            if (date in _state.value.completedDates) continue
            val assets = groups[date].orEmpty().mapIndexed { index, item ->
            CreateMomentAssetRequest(
                immichAssetId = requireNotNull(item.assetId),
                assetType = if (item.media.mimeType.startsWith("video/")) "VIDEO" else "IMAGE",
                sortOrder = index,
            )
        }
            val eventInstant = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
            val result = publishRepository.createMoment(current.content, current.location, assets, eventInstant)
            if (result.isFailure) {
                _state.update { it.copy(publishing = false, message = "$date 发布失败，已成功的日期会保留；点击重试继续，不会重复上传") }
                return@launch
            }
            _state.update { it.copy(completedDates = it.completedDates + date) }
        }
        _state.update { it.copy(publishing = false, published = true, publishedDate = dates.maxOrNull(), message = "已发布 ${dates.size} 个日期") }
    }

    private fun updateItem(index: Int, transform: (PublishMediaItem) -> PublishMediaItem) {
        _state.update { state ->
            state.copy(items = state.items.mapIndexed { itemIndex, item -> if (itemIndex == index) transform(item) else item })
        }
    }
}
