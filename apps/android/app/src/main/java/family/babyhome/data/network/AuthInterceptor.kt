package family.babyhome.data.network

import family.babyhome.data.auth.TokenStore
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AuthInterceptor @Inject constructor(private val tokenStore: TokenStore) : Interceptor {
    private val gson = Gson()
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalToken = tokenStore.accessToken()
        val request = if (originalToken.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $originalToken").build()
        }
        val response = chain.proceed(request)
        if (response.code != 401 || request.url.encodedPath.startsWith("/auth/")) return response

        synchronized(this) {
            val currentToken = tokenStore.accessToken()
            val retryToken = if (!currentToken.isNullOrBlank() && currentToken != originalToken) {
                currentToken
            } else {
                refreshAccessToken(request)
            }
            if (retryToken.isNullOrBlank()) {
                tokenStore.clear()
                return response
            }
            response.close()
            return chain.proceed(
                request.newBuilder().header("Authorization", "Bearer $retryToken").build(),
            )
        }
    }

    private fun refreshAccessToken(failedRequest: Request): String? {
        val refreshToken = tokenStore.refreshToken() ?: return null
        val refreshUrl = failedRequest.url.newBuilder()
            .encodedPath("/auth/refresh")
            .query(null)
            .build()
        val body = gson.toJson(mapOf("refreshToken" to refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(refreshUrl).post(body).build()
        return runCatching {
            refreshClient.newCall(request).execute().use { refreshResponse ->
                if (!refreshResponse.isSuccessful) return@use null
                val payload = gson.fromJson(refreshResponse.body?.charStream(), LoginResponse::class.java)
                tokenStore.save(payload.data.accessToken, payload.data.refreshToken)
                payload.data.accessToken
            }
        }.getOrNull()
    }
}
