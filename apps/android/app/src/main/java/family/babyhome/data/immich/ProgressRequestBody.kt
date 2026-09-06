package family.babyhome.data.immich

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

class ProgressRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val length: Long,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = mediaType
    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选媒体" }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var uploaded = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                uploaded += read
                if (length > 0) onProgress((uploaded.toFloat() / length).coerceIn(0f, 1f))
            }
        }
    }
}

