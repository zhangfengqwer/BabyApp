package family.babyhome.update

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface AppUpdateApi {
    @GET suspend fun version(@Url url: String): AppVersionResponse

    @Streaming
    @GET
    suspend fun download(@Url url: String): ResponseBody
}

data class AppVersionResponse(val success: Boolean, val data: AppRelease)

data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val publishedAt: String,
    val sizeBytes: Long,
    val downloadPath: String,
)
