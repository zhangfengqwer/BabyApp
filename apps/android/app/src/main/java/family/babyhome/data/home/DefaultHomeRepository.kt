package family.babyhome.data.home

import family.babyhome.data.network.BabyApi
import family.babyhome.data.settings.ServerConfigStore
import family.babyhome.domain.home.HomeData
import family.babyhome.domain.home.HomeRepository
import family.babyhome.data.immich.ImmichAssetService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultHomeRepository @Inject constructor(
    private val api: BabyApi,
    private val configStore: ServerConfigStore,
    private val assetService: ImmichAssetService,
) : HomeRepository {
    override suspend fun load() = runCatching {
        val baseUrl = configStore.activeConfig.first().babyServerUrl.trimEnd('/')
        val baby = api.babies("$baseUrl/babies").data.firstOrNull()
            ?: error("还没有宝宝资料，请让管理员先创建")
        val moments = api.moments("$baseUrl/babies/${baby.id}/moments?limit=10").data.items.map { moment ->
            moment.copy(
                assets = moment.assets.map { asset ->
                    asset.copy(thumbnailUrl = assetService.thumbnailUrl(baseUrl, asset.immichAssetId))
                },
            )
        }
        HomeData(baby, moments)
    }
}
