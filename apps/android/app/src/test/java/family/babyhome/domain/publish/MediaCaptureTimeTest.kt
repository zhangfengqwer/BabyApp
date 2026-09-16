package family.babyhome.domain.publish

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MediaCaptureTimeTest {
    @Test fun `QuickTime zero epoch is not a capture date`() {
        assertNull(MediaCaptureTime.usable(Instant.parse("1904-01-01T00:00:00Z")))
        assertEquals("2026-09-16", PublishDatePlanner.date(Instant.parse("1904-01-01T00:00:00Z"), "2026-09-16", true, ZoneId.of("Asia/Shanghai")))
    }
    @Test fun `future metadata is ignored`() {
        assertNull(MediaCaptureTime.usable(Instant.parse("2099-01-01T00:00:00Z"), Instant.parse("2026-09-16T00:00:00Z")))
    }
    @Test fun `real capture time is preserved`() {
        val captured = Instant.parse("2026-09-05T10:00:00Z")
        assertEquals(captured, MediaCaptureTime.usable(captured, Instant.parse("2026-09-16T00:00:00Z")))
    }
}
