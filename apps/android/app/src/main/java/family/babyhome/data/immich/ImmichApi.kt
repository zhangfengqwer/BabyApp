package family.babyhome.data.immich

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

interface ImmichApi {
    @Multipart
    @POST
    suspend fun uploadAsset(
        @Url url: String,
        @Part assetData: MultipartBody.Part,
        @Part("fileCreatedAt") fileCreatedAt: RequestBody,
        @Part("fileModifiedAt") fileModifiedAt: RequestBody,
        @Part("isFavorite") isFavorite: RequestBody,
    ): ImmichUploadResponse
}

data class ImmichUploadResponse(val id: String, val status: String? = null)
