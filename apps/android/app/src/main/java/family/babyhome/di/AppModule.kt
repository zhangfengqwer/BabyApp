package family.babyhome.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import family.babyhome.data.network.BabyApiClient
import family.babyhome.data.network.BabyApi
import family.babyhome.data.network.AuthInterceptor
import family.babyhome.data.network.HealthApi
import family.babyhome.data.immich.ImmichApi
import android.content.ContentResolver
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import family.babyhome.data.auth.DefaultAuthRepository
import family.babyhome.data.home.DefaultHomeRepository
import family.babyhome.data.settings.DefaultServerSettingsRepository
import family.babyhome.domain.auth.AuthRepository
import family.babyhome.domain.home.HomeRepository
import family.babyhome.data.timeline.DefaultTimelineRepository
import family.babyhome.domain.timeline.TimelineRepository
import family.babyhome.domain.settings.ServerSettingsRepository
import family.babyhome.update.AppUpdateApi
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindServerSettingsRepository(
        implementation: DefaultServerSettingsRepository,
    ): ServerSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindHomeRepository(implementation: DefaultHomeRepository): HomeRepository

    @Binds
    @Singleton
    abstract fun bindTimelineRepository(implementation: DefaultTimelineRepository): TimelineRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideRetrofit(authInterceptor: AuthInterceptor): Retrofit = BabyApiClient.create(authInterceptor)

    @Provides
    @Singleton
    fun provideHealthApi(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)

    @Provides
    @Singleton
    fun provideBabyApi(retrofit: Retrofit): BabyApi = retrofit.create(BabyApi::class.java)

    @Provides
    @Singleton
    fun provideImmichApi(retrofit: Retrofit): ImmichApi = retrofit.create(ImmichApi::class.java)

    @Provides
    @Singleton
    fun provideAppUpdateApi(retrofit: Retrofit): AppUpdateApi = retrofit.create(AppUpdateApi::class.java)

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver
}
