package family.babyhome.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConfigTest {
    @Test
    fun `uses current Windows LAN address by default`() {
        val config = ServerConfig()

        assertEquals("http://192.168.0.40:8080", config.babyServerUrl)
        assertEquals("http://192.168.0.40:2283", config.immichUrl)
        assertEquals("zhangfengqwer", DEFAULT_HOME_USERNAME)
    }
}
