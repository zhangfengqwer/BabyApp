package family.babyhome.data.family
import family.babyhome.data.network.*
import family.babyhome.data.settings.ServerConfigStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
data class FamilyPage(val members: List<FamilyUser>, val canManage: Boolean)
class FamilyRepository @Inject constructor(private val api:BabyApi, private val config:ServerConfigStore) {
    private suspend fun url():String {
        val base=config.activeConfig.first().babyServerUrl.trimEnd('/')
        val baby=api.babies("$base/babies").data.firstOrNull() ?: error("暂无宝宝资料")
        return "$base/babies/${baby.id}/family"
    }
    suspend fun load():FamilyPage {
        val base=config.activeConfig.first().babyServerUrl.trimEnd('/')
        val baby=api.babies("$base/babies").data.firstOrNull() ?: error("暂无宝宝资料")
        return FamilyPage(api.family("$base/babies/${baby.id}/family").data,baby.canEdit)
    }
    suspend fun create(username:String,relationship:String) { api.createFamily(url(),FamilyRequest(username.trim().lowercase(),relationship.trim())) }
    suspend fun edit(id:String,relationship:String) { api.editFamily("${url()}/$id",RelationshipRequest(relationship.trim())) }
}
