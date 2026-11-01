package app.focus.lockin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SealPolicyTest {
    private val five = 5 * 60_000L

    @Test
    fun sealProgressionCapsAtFortyMinutes() {
        assertEquals(five, SealPolicy.sealMillis(1, five))
        assertEquals(five * 2, SealPolicy.sealMillis(2, five))
        assertEquals(five * 4, SealPolicy.sealMillis(3, five))
        assertEquals(five * 8, SealPolicy.sealMillis(4, five))
        assertEquals(five * 8, SealPolicy.sealMillis(9, five))
    }

    @Test
    fun relaunchDuringSealDoesNotStack() {
        val first = SealPolicy.onLaunch(1_000L, 0L, 0, five)
        assertTrue(first.counted)
        assertEquals(1, first.strikes)
        val again = SealPolicy.onLaunch(first.until - 1, first.until, first.strikes, five)
        assertFalse(again.counted)
        assertEquals(first.until, again.until)
        assertEquals(1, again.strikes)
    }

    @Test
    fun weekdayWindowAndOvernight() {
        val week = SealPolicy.Window(setOf(1, 2, 3, 4, 5), 9 * 60, 12 * 60 + 30)
        assertTrue(SealPolicy.inWindow(1, 9 * 60, listOf(week)))
        assertFalse(SealPolicy.inWindow(1, 12 * 60 + 30, listOf(week)))
        assertFalse(SealPolicy.inWindow(6, 10 * 60, listOf(week)))
        val night = SealPolicy.Window(setOf(5), 22 * 60, 2 * 60)
        assertTrue(SealPolicy.inWindow(5, 23 * 60, listOf(night)))
        assertTrue(SealPolicy.inWindow(5, 1 * 60, listOf(night)))
        assertFalse(SealPolicy.inWindow(5, 15 * 60, listOf(night)))
    }

    @Test
    fun toxicHintHitsKnownPackagesOnlyAsSuggestion() {
        assertTrue(ToxicHint.looksToxic("com.instagram.android", "Instagram"))
        assertTrue(ToxicHint.looksToxic("com.example.tiktok.mod", "Tok"))
        assertFalse(ToxicHint.looksToxic("com.google.android.dialer", "Téléphone"))
    }

    @Test
    fun phoneAndSettingsStayAllowed() {
        assertTrue("com.android.settings" in ALWAYS_ALLOWED)
        assertTrue("com.google.android.dialer" in ALWAYS_ALLOWED)
        assertTrue("com.android.launcher3" in ALWAYS_ALLOWED)
    }
}
