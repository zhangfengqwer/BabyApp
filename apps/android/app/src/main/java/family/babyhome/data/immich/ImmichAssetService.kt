package family.babyhome.data.immich

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImmichAssetService @Inject constructor() {
    fun thumbnailUrl(serverUrl: String, assetId: String): String =
        "${serverUrl.trimEnd('/')}/media/assets/$assetId/view?size=thumbnail"

    fun previewUrl(serverUrl: String, assetId: String): String =
        "${serverUrl.trimEnd('/')}/media/assets/$assetId/view?size=preview"

    fun videoUrl(serverUrl: String, assetId: String): String =
        "${serverUrl.trimEnd('/')}/media/assets/$assetId/view?size=fullsize"
}

