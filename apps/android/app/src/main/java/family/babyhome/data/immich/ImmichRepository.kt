package family.babyhome.data.immich

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import family.babyhome.data.settings.ServerConfigStore
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class LocalMedia(val uri: Uri, val fileName: String, val mimeType: String, val size: Long)

@Singleton
class ImmichRepository @Inject constructor(
    private val resolver: ContentResolver,
    private val api: ImmichApi,
    private val auth: ImmichAuthManager,
    private val configStore: ServerConfigStore,
) {
    fun describe(uri: Uri): LocalMedia {
        var name = "media-${System.currentTimeMillis()}"
        var size = -1L
        val cursor: Cursor? = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        cursor.use {
            if (it?.moveToFirst() == true) {
                name = it.getString(0) ?: name
                size = if (it.isNull(1)) -1L else it.getLong(1)
            }
        }
        return LocalMedia(uri, name, resolver.getType(uri) ?: "application/octet-stream", size)
    }

    suspend fun upload(media: LocalMedia, onProgress: (Float) -> Unit): Result<ImmichUploadResponse> = runCatching {
        check(auth.isAuthenticated()) { "请先登录" }
        val serverUrl = configStore.activeConfig.first().babyServerUrl.trimEnd('/')
        val body = ProgressRequestBody(
            resolver = resolver,
            uri = media.uri,
            mediaType = media.mimeType.toMediaTypeOrNull(),
            length = media.size,
            onProgress = onProgress,
        )
        val part = MultipartBody.Part.createFormData("assetData", media.fileName, body)
        val now = Instant.now().toString().toRequestBody("text/plain".toMediaTypeOrNull())
        api.uploadAsset(
            url = "$serverUrl/media/assets",
            assetData = part,
            fileCreatedAt = now,
            fileModifiedAt = now,
            isFavorite = "false".toRequestBody("text/plain".toMediaTypeOrNull()),
        )
    }
}
