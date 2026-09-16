package family.babyhome.data.baby

import family.babyhome.data.network.BabyApi
import family.babyhome.data.network.BabyDto
import family.babyhome.data.network.EditBabyRequest
import family.babyhome.data.settings.ServerConfigStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class BabyProfileRepository @Inject constructor(private val api: BabyApi, private val config: ServerConfigStore) {
    private suspend fun url() = config.activeConfig.first().babyServerUrl.trimEnd('/')
    suspend fun load(): BabyDto = api.babies("${url()}/babies").data.firstOrNull() ?: error("尚未创建宝宝资料")
    suspend fun save(id: String, request: EditBabyRequest) = api.editBaby("${url()}/babies/$id", request).data
}
