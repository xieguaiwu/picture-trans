package com.xieguiawu.picturetrans.transfer

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferTrackerTest {

    @Test fun `begin update finish lifecycle`() = runTest {
        val tracker = TransferTracker()
        val h = tracker.begin(Direction.DOWNLOAD, "movie.mp4", 1000)
        assertEquals(1, tracker.active.value.size)
        assertEquals(1000, tracker.active.value.first().bytesTotal)

        h.addBytes(400)
        h.addBytes(300)
        // 节流粒度：active 的 bytesDone 是近似值（≤700），精确值在 finish 落账
        assertTrue(tracker.active.value.first().bytesDone in 400..700)

        h.finish(ok = true)
        assertEquals(0, tracker.active.value.size)
        assertEquals(1, tracker.history.value.size)
        val entry = tracker.history.value.first()
        assertEquals(700, entry.bytes)
        assertTrue(entry.ok)
        assertEquals("movie.mp4", entry.fileName)
    }

    @Test fun `finish is idempotent`() = runTest {
        val tracker = TransferTracker()
        val h = tracker.begin(Direction.UPLOAD, "a.txt", -1)
        h.addBytes(10)
        h.finish(ok = true)
        h.finish(ok = false, message = "late error") // 不应重复记录
        assertEquals(1, tracker.history.value.size)
        assertTrue(tracker.history.value.first().ok)
    }

    @Test fun `failed finish carries message`() = runTest {
        val tracker = TransferTracker()
        tracker.begin(Direction.UPLOAD, "x.bin", -1).finish(ok = false, message = "disk full")
        val entry = tracker.history.value.first()
        assertTrue(!entry.ok)
        assertEquals("disk full", entry.message)
    }

    @Test fun `history capped at 50 newest first`() = runTest {
        val tracker = TransferTracker()
        repeat(60) { i ->
            tracker.begin(Direction.UPLOAD, "f$i", -1).finish(ok = true)
        }
        assertEquals(50, tracker.history.value.size)
        assertEquals("f59", tracker.history.value.first().fileName)
        assertEquals("f10", tracker.history.value.last().fileName)
    }
}
