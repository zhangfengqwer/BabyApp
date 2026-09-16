package family.babyhome.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.DELETE

interface BabyApi {
    @PATCH suspend fun editBaby(@Url url: String, @Body request: EditBabyRequest): BabyResponse
    @POST suspend fun homeLogin(@Url url: String, @Header("X-Home-Access-Key") accessKey: String, @Header("X-Family-Username") username: String? = null): LoginResponse
    @GET suspend fun family(@Url url: String): FamilyResponse
    @POST suspend fun createFamily(@Url url: String, @Body request: FamilyRequest): EmptyResponse
    @PATCH suspend fun editFamily(@Url url: String, @Body request: RelationshipRequest): EmptyResponse
    @GET suspend fun moment(@Url url: String): MomentResponse
    @GET suspend fun comments(@Url url: String): CommentsResponse
    @POST suspend fun comment(@Url url: String, @Body body: CommentRequest): EmptyResponse
    @POST suspend fun like(@Url url: String): MomentResponse
    @DELETE suspend fun unlike(@Url url: String): MomentResponse
    @PATCH suspend fun editMoment(@Url url: String, @Body body: MomentEditRequest): MomentResponse
    @DELETE suspend fun deleteMoment(@Url url: String): EmptyResponse
    @DELETE suspend fun deleteMomentAsset(@Url url: String): EmptyResponse
    @POST
    suspend fun login(@Url url: String, @Body request: LoginRequest): LoginResponse

    @GET
    suspend fun babies(@Url url: String): BabiesResponse

    @GET
    suspend fun moments(@Url url: String): MomentsResponse

    @POST
    suspend fun createMoment(@Url url: String, @Body request: CreateMomentRequest): CreateMomentResponse
}

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val success: Boolean, val data: LoginData)
data class LoginData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto,
)
data class UserDto(val id: String, val username: String, val nickname: String, val role: String)
data class FamilyUser(val id: String, val username: String, val nickname: String, val role: String, val relationship: String)
data class FamilyResponse(val success: Boolean, val data: List<FamilyUser>)
data class FamilyRequest(val username: String, val relationship: String)
data class RelationshipRequest(val relationship: String)

data class BabiesResponse(val success: Boolean, val data: List<BabyDto>)
data class BabyDto(
    val id: String,
    val name: String,
    val nickname: String?,
    val birthday: String,
    val avatarAssetId: String?,
    val description: String?,
    val canEdit: Boolean = false,
)
data class BabyResponse(val success: Boolean, val data: BabyDto)
data class EditBabyRequest(val name: String, val nickname: String, val birthday: String, val description: String, val avatarAssetId: String? = null)

data class MomentsResponse(val success: Boolean, val data: MomentsData)
data class MomentsData(val items: List<MomentDto>, val nextCursor: String?)
data class MomentDto(
    val id: String,
    val content: String?,
    val eventDate: String,
    val location: String?,
    val author: MomentAuthorDto,
    val assets: List<MomentAssetDto>,
    val _count: MomentCountsDto,
    val likedByMe: Boolean,
    val canEdit: Boolean = false,
)
data class MomentAuthorDto(val id: String, val nickname: String, val avatar: String?)
data class MomentAssetDto(
    val id: String,
    val immichAssetId: String,
    val assetType: String,
    val sortOrder: Int,
    val thumbnailUrl: String? = null,
)
data class MomentCountsDto(val comments: Int, val likes: Int)

data class CreateMomentRequest(
    val content: String?,
    val eventDate: String,
    val location: String?,
    val assets: List<CreateMomentAssetRequest>,
)
data class CreateMomentAssetRequest(val immichAssetId: String, val assetType: String, val sortOrder: Int)
data class CreateMomentResponse(val success: Boolean, val data: CreatedMomentDto)
data class CreatedMomentDto(val id: String)
data class MomentResponse(val success: Boolean, val data: MomentDto)
data class CommentRequest(val content: String)
data class CommentUser(val id: String, val nickname: String)
data class CommentItem(val id: String, val content: String, val user: CommentUser, val createdAt: String)
data class CommentPage(val items: List<CommentItem>, val nextCursor: String?)
data class CommentsResponse(val success: Boolean, val data: CommentPage)
data class EmptyResponse(val success: Boolean)
data class MomentEditRequest(val content: String? = null, val location: String? = null,
    val eventDate: String? = null, val coverAssetId: String? = null)
