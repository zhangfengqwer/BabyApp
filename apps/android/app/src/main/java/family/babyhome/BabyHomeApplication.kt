package family.babyhome

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import family.babyhome.data.auth.TokenStore
import family.babyhome.data.network.AuthInterceptor
import family.babyhome.data.network.BabyApiClient

@HiltAndroidApp
class BabyHomeApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(BabyApiClient.createOkHttp(AuthInterceptor(TokenStore(this))))
        .build()
}
