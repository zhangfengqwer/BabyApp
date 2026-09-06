package family.babyhome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.domain.home.HomeData
import family.babyhome.domain.home.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(val loading: Boolean = true, val data: HomeData? = null, val error: String? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: HomeRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        repository.load().onSuccess { data ->
            _state.value = HomeUiState(loading = false, data = data)
        }.onFailure {
            _state.value = HomeUiState(loading = false, error = "暂时无法加载，请检查家庭服务器和 Tailscale")
        }
    }
}

