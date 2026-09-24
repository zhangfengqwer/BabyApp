package family.babyhome.ui.publish

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore

internal data class LocalDeleteCandidate(val uri: Uri, val name: String)

internal object LocalMediaDeletion {
    /** MediaStore.Files and document URIs must become image/video item URIs for delete requests. */
    fun itemUri(context: Context, original: Uri, mimeType: String?): Uri? = runCatching {
        val mediaUri = if (Build.VERSION.SDK_INT >= 29 && DocumentsContract.isDocumentUri(context, original)) {
            MediaStore.getMediaUri(context, original) ?: return@runCatching null
        } else original
        if (mediaUri.authority != MediaStore.AUTHORITY || "picker" in mediaUri.pathSegments) return@runCatching null
        val volume = mediaUri.pathSegments.firstOrNull() ?: return@runCatching null
        val collection = when {
            mimeType?.startsWith("image/") == true -> if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(volume) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType?.startsWith("video/") == true -> if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> return@runCatching null
        }
        ContentUris.withAppendedId(collection, ContentUris.parseId(mediaUri))
    }.getOrNull()

    fun exists(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.query(uri, arrayOf(BaseColumns._ID), null, null, null)?.use { it.moveToFirst() } == true
    }.getOrDefault(true)

    fun previouslyPublishedOnDevice(context: Context): List<LocalDeleteCandidate> =
        PublishedMediaHistory.read(context).mapNotNull { text ->
            val original = runCatching { Uri.parse(text) }.getOrNull() ?: return@mapNotNull null
            val mimeType = runCatching { context.contentResolver.getType(original) }.getOrNull()
            val item = itemUri(context, original, mimeType) ?: return@mapNotNull null
            val name = runCatching {
                context.contentResolver.query(item, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: return@mapNotNull null
            LocalDeleteCandidate(item, name)
        }.distinctBy { it.uri }.take(100)
}
