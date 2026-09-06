package family.babyhome.data.immich

import family.babyhome.data.auth.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImmichAuthManager @Inject constructor(private val tokens: TokenStore) {
    fun isAuthenticated(): Boolean = !tokens.accessToken().isNullOrBlank()
}

