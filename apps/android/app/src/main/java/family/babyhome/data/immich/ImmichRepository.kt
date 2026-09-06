package family.babyhome.data.immich

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import family.babyhome.data.settings.ServerConfigStore
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class LocalMedia(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val capturedAt: Instant? = null,
)

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
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        return LocalMedia(uri, name, mimeType, size, readCapturedAt(uri, mimeType))
    }

    private fun readCapturedAt(uri: Uri, mimeType: String): Instant? {
        if (mimeType.startsWith("image/")) {
            val exifInstant = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    raw?.let {
                        LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                            .atZone(ZoneId.systemDefault()).toInstant()
                    }
                }
            }.getOrNull()
            if (exifInstant != null) return exifInstant
        }
        return runCatching {
            resolver.query(uri, arrayOf(android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) Instant.ofEpochMilli(it.getLong(0)) else null
            }
        }.getOrNull()
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
        val mediaTime = (media.capturedAt ?: Instant.now()).toString().toRequestBody("text/plain".toMediaTypeOrNull())
        api.uploadAsset(
            url = "$serverUrl/media/assets",
            assetData = part,
            fileCreatedAt = mediaTime,
            fileModifiedAt = mediaTime,
            isFavorite = "false".toRequestBody("text/plain".toMediaTypeOrNull()),
        )
    }
}
