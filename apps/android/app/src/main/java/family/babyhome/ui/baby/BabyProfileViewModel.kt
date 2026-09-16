package family.babyhome.ui.baby

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.baby.BabyProfileRepository
import family.babyhome.data.immich.ImmichRepository
import family.babyhome.data.immich.ImmichAssetService
import family.babyhome.data.network.EditBabyRequest
import family.babyhome.data.settings.ServerConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import javax.inject.Inject

data class BabyProfileState(
    val id: String = "", val name: String = "", val nickname: String = "", val birthday: String = "",
    val description: String = "", val avatarUrl: String? = null, val selectedAvatar: Uri? = null,
    val uploadedAvatarId: String? = null, val loading: Boolean = true, val saving: Boolean = false,
    val saved: Boolean = false, val error: String? = null, val canEdit: Boolean = false,
)

@HiltViewModel
class BabyProfileViewModel @Inject constructor(
    private val repository: BabyProfileRepository,
    private val immich: ImmichRepository,
    private val assets: ImmichAssetService,
    private val config: ServerConfigStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(BabyProfileState())
    val state = mutable.asStateFlow()
    init { reload() }
    fun reload() = viewModelScope.launch {
        mutable.update { it.copy(loading = true, error = null) }
        try {
            val baby = repository.load()
            val server = config.activeConfig.first().babyServerUrl
            mutable.value = BabyProfileState(id = baby.id, name = baby.name, nickname = baby.nickname.orEmpty(), birthday = baby.birthday.take(10), description = baby.description.orEmpty(), avatarUrl = baby.avatarAssetId?.let { assets.thumbnailUrl(server, it) }, loading = false, canEdit = baby.canEdit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutable.update { it.copy(loading = false, error = "无法读取宝宝资料，请检查连接后重试") }
        }
    }
    fun fields(name: String = state.value.name, nickname: String = state.value.nickname, birthday: String = state.value.birthday, description: String = state.value.description) {
        if (!state.value.saving) mutable.update { it.copy(name = name, nickname = nickname, birthday = birthday, description = description, error = null) }
    }
    fun avatar(uri: Uri) { if (!state.value.saving) mutable.update { it.copy(selectedAvatar = uri, uploadedAvatarId = null) } }
    fun save() = viewModelScope.launch {
        val current = state.value
        if (current.saving || !current.canEdit || current.loading) return@launch
        if (current.name.isBlank() || runCatching { LocalDate.parse(current.birthday).isAfter(LocalDate.now()) }.getOrDefault(true)) {
            mutable.update { it.copy(error = "请填写宝宝姓名和正确生日") }; return@launch
        }
        mutable.update { it.copy(saving = true, error = null) }
        try {
            var avatarId = current.uploadedAvatarId
            if (current.selectedAvatar != null && avatarId == null) {
                avatarId = withContext(Dispatchers.IO) { immich.upload(immich.describe(current.selectedAvatar)) {}.getOrThrow().id }
                mutable.update { it.copy(uploadedAvatarId = avatarId) }
            }
            repository.save(current.id, EditBabyRequest(current.name.trim(), current.nickname.trim(), current.birthday, current.description.trim(), avatarId))
            mutable.update { it.copy(saving = false, saved = true) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutable.update { it.copy(saving = false, error = "保存失败，请检查连接后重试；已上传头像不会重复上传") }
        }
    }
}
