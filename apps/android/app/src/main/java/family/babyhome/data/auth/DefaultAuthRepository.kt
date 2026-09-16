package family.babyhome.data.auth

import family.babyhome.data.network.BabyApi
import family.babyhome.data.network.LoginRequest
import family.babyhome.data.settings.DefaultServerSettingsRepository
import family.babyhome.domain.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAuthRepository @Inject constructor(
    private val api: BabyApi,
    private val tokens: TokenStore,
) : AuthRepository {
    override fun isLoggedIn() = !tokens.accessToken().isNullOrBlank()

    override suspend fun homeLogin(serverUrl: String, accessKey: String, username: String?) = runCatching {
        val baseUrl = DefaultServerSettingsRepository.normalizeServerUrl(serverUrl)
        val response = api.homeLogin("$baseUrl/auth/home", accessKey, username)
        tokens.save(response.data.accessToken, response.data.refreshToken)
        response.data.user
    }

    override suspend fun login(serverUrl: String, username: String, password: String) = runCatching {
        val baseUrl = DefaultServerSettingsRepository.normalizeServerUrl(serverUrl)
        val response = api.login("$baseUrl/auth/login", LoginRequest(username.trim(), password))
        tokens.save(response.data.accessToken, response.data.refreshToken)
        response.data.user
    }

    override fun logout() = tokens.clear()
}
