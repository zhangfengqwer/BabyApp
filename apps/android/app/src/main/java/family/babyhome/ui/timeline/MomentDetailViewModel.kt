package family.babyhome.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.network.*
import family.babyhome.data.immich.ImmichAssetService
import family.babyhome.data.settings.ServerConfigStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class DetailState(val moment: MomentDto? = null, val comments: List<CommentItem> = emptyList(),
    val cursor: String? = null, val busy: Boolean = false, val error: String? = null, val deleted: Boolean = false)

@HiltViewModel
class MomentDetailViewModel @Inject constructor(
    handle: SavedStateHandle, private val api: BabyApi, private val config: ServerConfigStore,
    private val assets: ImmichAssetService,
): ViewModel() {
    private val id = requireNotNull(handle.get<String>("momentId"))
    private val mutable = MutableStateFlow(DetailState())
    val state = mutable.asStateFlow()
    private suspend fun url() = config.activeConfig.first().babyServerUrl.trimEnd('/') + "/moments/" + id
    init { reload() }
    private fun action(block: suspend () -> Unit) {
        if(mutable.value.busy) return
        viewModelScope.launch {
            mutable.update { it.copy(busy=true,error=null) }
            try { block() } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(error="操作失败，请检查网络或重新登录后重试") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    private suspend fun fetch() {
        val base = config.activeConfig.first().babyServerUrl
        val moment = api.moment(url()).data
        val comments = api.comments(url()+"/comments").data
        mutable.update { it.copy(moment=moment.copy(assets=moment.assets.map { a ->
            a.copy(thumbnailUrl=assets.thumbnailUrl(base,a.immichAssetId)) }),
            comments=comments.items,cursor=comments.nextCursor) }
    }
    fun reload() = action { fetch() }
    fun like() = action {
        if(mutable.value.moment?.likedByMe == true) api.unlike(url()+"/like") else api.like(url()+"/like")
        fetch()
    }
    fun comment(text:String) = action { api.comment(url()+"/comments",CommentRequest(text)); fetch() }
    fun edit(content:String, location:String) = action { api.editMoment(url(),MomentEditRequest(content=content,location=location)); fetch() }
    fun cover(id:String) = action { api.editMoment(url(),MomentEditRequest(coverAssetId=id)); fetch() }
    fun delete() = action { api.deleteMoment(url()); mutable.update { it.copy(deleted=true) } }
    fun moreComments() = action {
        val cursor=mutable.value.cursor
        if(cursor != null) {
            val page=api.comments(url()+"/comments?cursor="+cursor).data
            mutable.update { it.copy(comments=(it.comments+page.items).distinctBy { c->c.id },cursor=page.nextCursor) }
        }
    }
}
