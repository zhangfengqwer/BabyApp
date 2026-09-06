package family.babyhome.data.timeline

import family.babyhome.data.immich.ImmichAssetService
import family.babyhome.data.network.BabyApi
import family.babyhome.data.settings.ServerConfigStore
import family.babyhome.domain.timeline.TimelinePage
import family.babyhome.domain.timeline.TimelineRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTimelineRepository @Inject constructor(
    private val api: BabyApi,
    private val configStore: ServerConfigStore,
    private val assetService: ImmichAssetService,
) : TimelineRepository {
    override suspend fun load(cursor: String?, limit: Int) = runCatching {
        val baseUrl = configStore.activeConfig.first().babyServerUrl.trimEnd('/')
        val baby = api.babies("$baseUrl/babies").data.firstOrNull() ?: error("还没有宝宝资料")
        val cursorQuery = cursor?.let { "&cursor=$it" }.orEmpty()
        val page = api.moments("$baseUrl/babies/${baby.id}/moments?limit=$limit$cursorQuery").data
        val moments = page.items.map { moment ->
            moment.copy(
                assets = moment.assets.map { asset ->
                    asset.copy(thumbnailUrl = assetService.thumbnailUrl(baseUrl, asset.immichAssetId))
                },
            )
        }
        TimelinePage(
            baby = baby,
            avatarUrl = baby.avatarAssetId?.let { assetService.thumbnailUrl(baseUrl, it) },
            moments = moments,
            nextCursor = page.nextCursor,
        )
    }
}
