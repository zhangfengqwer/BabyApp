package family.babyhome.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlNormalizerTest {
    @Test
    fun `normalizes whitespace and trailing slash`() {
        assertEquals(
            "http://baby-server:8080",
            DefaultServerSettingsRepository.normalizeServerUrl(" http://baby-server:8080/ "),
        )
    }

    @Test
    fun `rejects unsupported scheme`() {
        assertThrows(IllegalArgumentException::class.java) {
            DefaultServerSettingsRepository.normalizeServerUrl("ftp://baby-server")
        }
    }
}
