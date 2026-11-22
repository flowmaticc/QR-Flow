package net.hilson.qrieux.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatGuardTest {

    private fun RepeatGuard.feed(vararg frames: String?) = frames.map { accept(it) }

    @Test
    fun sameCodeIsReportedOnceWhileInView() {
        val g = RepeatGuard(clearAfterMisses = 3)
        assertEquals(listOf(true, false, false, false), g.feed("A", "A", "A", "A"))
    }

    @Test
    fun blurryFramesDoNotReleaseTheCode() {
        val g = RepeatGuard(clearAfterMisses = 3)
        // Two failed decodes in the middle (blur) are not enough to report "A" again.
        assertEquals(listOf(true, false, false, false), g.feed("A", null, null, "A"))
    }

    @Test
    fun codeIsReportedAgainAfterLeavingTheFrame() {
        val g = RepeatGuard(clearAfterMisses = 3)
        assertEquals(listOf(true, false, false, false, true), g.feed("A", null, null, null, "A"))
    }

    @Test
    fun differentCodeIsReportedStraightAway() {
        val g = RepeatGuard(clearAfterMisses = 3)
        assertEquals(listOf(true, true, false), g.feed("A", "B", "B"))
    }
}
