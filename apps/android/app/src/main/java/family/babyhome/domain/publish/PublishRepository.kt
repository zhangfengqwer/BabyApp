package family.babyhome.domain.publish

import family.babyhome.data.network.BabyApi
import family.babyhome.data.network.CreateMomentAssetRequest
import family.babyhome.data.network.CreateMomentRequest
import family.babyhome.data.settings.ServerConfigStore
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublishRepository @Inject constructor(
    private val api: BabyApi,
    private val configStore: ServerConfigStore,
) {
    suspend fun createMoment(
        content: String,
        location: String,
        assets: List<CreateMomentAssetRequest>,
        eventDate: String,
    ): Result<String> = runCatching {
        val serverUrl = configStore.activeConfig.first().babyServerUrl.trimEnd('/')
        val baby = api.babies("$serverUrl/babies").data.firstOrNull() ?: error("还没有宝宝资料")
        val uniqueAssets = assets
            .distinctBy(CreateMomentAssetRequest::immichAssetId)
            .mapIndexed { index, asset -> asset.copy(sortOrder = index) }
        api.createMoment(
            "$serverUrl/babies/${baby.id}/moments",
            CreateMomentRequest(
                content = content.trim().ifBlank { null },
                eventDate = eventDate,
                location = location.trim().ifBlank { null },
                assets = uniqueAssets,
            ),
        ).data.id
    }
}
