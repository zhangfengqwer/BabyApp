package family.babyhome.data.settings

const val DEFAULT_BABY_SERVER_URL = "http://192.168.0.26:8080"
const val DEFAULT_IMMICH_URL = "http://192.168.0.26:2283"
const val TAILSCALE_BABY_SERVER_URL = "http://desktop-rcsbtks:8080"
const val TAILSCALE_IMMICH_URL = "http://desktop-rcsbtks:2283"
const val DEFAULT_HOME_USERNAME = "zhangfengqwer"

data class ServerConfig(
    val babyServerUrl: String = DEFAULT_BABY_SERVER_URL,
    val immichUrl: String = DEFAULT_IMMICH_URL,
)
