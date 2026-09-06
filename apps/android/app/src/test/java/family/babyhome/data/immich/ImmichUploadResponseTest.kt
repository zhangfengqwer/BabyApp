package family.babyhome.data.immich

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmichUploadResponseTest {
    @Test
    fun `parses Immich v3 duplicate response`() {
        val response = Gson().fromJson(
            """{"status":"duplicate","id":"7347ffc1-03cf-409f-9d79-4a04f99d8f9c"}""",
            ImmichUploadResponse::class.java,
        )

        assertEquals("duplicate", response.status)
        assertEquals("7347ffc1-03cf-409f-9d79-4a04f99d8f9c", response.id)
    }
}
