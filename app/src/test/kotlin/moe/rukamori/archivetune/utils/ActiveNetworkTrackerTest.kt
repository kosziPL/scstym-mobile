/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveNetworkTrackerTest {
    @Test
    fun reportsOnlineToOnlineDefaultNetworkChange() {
        val tracker = ActiveNetworkTracker("wifi")

        assertTrue(tracker.onAvailable("cellular"))
        assertTrue(tracker.isActive("cellular"))
    }

    @Test
    fun ignoresInitialConnectionAndDuplicateCallbacks() {
        val tracker = ActiveNetworkTracker<String>(null)

        assertFalse(tracker.onAvailable("wifi"))
        assertFalse(tracker.onAvailable("wifi"))
    }

    @Test
    fun losingOldNetworkAfterHandoffDoesNotClearNewNetwork() {
        val tracker = ActiveNetworkTracker("wifi")

        tracker.onAvailable("cellular")
        tracker.onLost("wifi")

        assertTrue(tracker.isActive("cellular"))
    }

    @Test
    fun detectsNewRouteEvenWhenOldNetworkIsLostFirst() {
        val tracker = ActiveNetworkTracker("wifi")

        tracker.onLost("wifi")

        assertTrue(tracker.onAvailable("cellular"))
    }
}
