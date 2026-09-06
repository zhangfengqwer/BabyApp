package family.babyhome.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.network.BabyDto
import family.babyhome.data.network.MomentDto
import family.babyhome.domain.timeline.TimelineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val baby: BabyDto? = null,
    val avatarUrl: String? = null,
    val moments: List<MomentDto> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val refreshVersion: Int = 0,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: TimelineRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    fun loadInitial() = viewModelScope.launch {
        val nextRefreshVersion = _state.value.refreshVersion + 1
        _state.update { it.copy(loading = true, error = null) }
        repository.load().onSuccess { page ->
            _state.value = TimelineUiState(
                baby = page.baby,
                avatarUrl = page.avatarUrl,
                moments = page.moments,
                nextCursor = page.nextCursor,
                loading = false,
                refreshVersion = nextRefreshVersion,
            )
        }.onFailure {
            _state.update { state -> state.copy(loading = false, error = "无法加载成长时间轴") }
        }
    }

    fun refresh() {
        if (_state.value.refreshing || _state.value.loadingMore || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            repository.load().onSuccess { page ->
                _state.update {
                    it.copy(
                        baby = page.baby,
                        avatarUrl = page.avatarUrl,
                        moments = page.moments,
                        nextCursor = page.nextCursor,
                        refreshing = false,
                        refreshVersion = it.refreshVersion + 1,
                    )
                }
            }.onFailure {
                _state.update { state -> state.copy(refreshing = false, error = "刷新失败，请检查网络") }
            }
        }
    }

    fun removeMoment(momentId: String) {
        _state.update { state -> state.copy(moments = state.moments.filterNot { it.id == momentId }) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.refreshing || current.loadingMore || current.nextCursor == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            repository.load(current.nextCursor).onSuccess { page ->
                _state.update {
                    it.copy(
                        moments = (it.moments + page.moments).distinctBy { moment -> moment.id },
                        nextCursor = page.nextCursor,
                        loadingMore = false,
                    )
                }
            }.onFailure {
                _state.update { it.copy(loadingMore = false, error = "加载更多失败") }
            }
        }
    }
}
