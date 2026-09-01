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

class YTPlayerUtilsProbeTest {
    @Test
    fun rejectsSignedStreamStatusesThatRequireAnotherClient() {
        listOf(403, 404, 410, 416).forEach { status ->
            assertTrue(YTPlayerUtils.isRejectedStreamProbeStatus(status))
        }
    }

    @Test
    fun keepsSuccessfulAndUnrelatedFailureStatuses() {
        listOf(200, 206, 400, 401, 429, 500).forEach { status ->
            assertFalse(YTPlayerUtils.isRejectedStreamProbeStatus(status))
        }
    }
}
