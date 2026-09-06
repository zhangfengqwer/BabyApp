package family.babyhome.domain.auth

import family.babyhome.data.network.UserDto

interface AuthRepository {
    fun isLoggedIn(): Boolean
    suspend fun homeLogin(serverUrl: String, accessKey: String): Result<UserDto>
    suspend fun login(serverUrl: String, username: String, password: String): Result<UserDto>
    fun logout()
}
