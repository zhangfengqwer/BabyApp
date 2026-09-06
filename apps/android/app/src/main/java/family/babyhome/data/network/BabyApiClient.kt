package family.babyhome.data.network

import family.babyhome.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object BabyApiClient {
    fun createOkHttp(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("x-api-key")
        }
        return OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    fun create(authInterceptor: AuthInterceptor): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(createOkHttp(authInterceptor))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
