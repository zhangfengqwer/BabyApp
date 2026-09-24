package family.babyhome.ui.publish

import android.content.Context
import android.net.Uri

internal object PublishedMediaHistory {
    private const val PREFS = "published_media"
    private const val KEY = "local_uris"

    fun read(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun mark(context: Context, uris: List<Uri>) {
        val values = read(context) + uris.map(Uri::toString)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, values).apply()
    }
}
