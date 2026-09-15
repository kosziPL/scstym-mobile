package moe.rukamori.archivetune.utils

import androidx.datastore.preferences.core.mutablePreferencesOf
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackAuthRepairTest {
    private val oldSession = PlaybackAuthState(cookie = "SAPISID=synthetic", visitorData = "visitor", dataSyncId = "old||user")

    @Test
    fun oldPlaybackResponseCannotOverwriteNewlyPersistedChannelWithSameCookie() {
        val preferences = mutablePreferencesOf(
            InnerTubeCookieKey to oldSession.cookie!!,
            VisitorDataKey to "visitor",
            DataSyncIdKey to "new||user",
        )
        preferences.applyPlaybackAuthRepair(oldSession, oldSession.copy(visitorData = "late-visitor", dataSyncId = "old||refreshed"))
        assertEquals("new||user", preferences[DataSyncIdKey])
        assertEquals("visitor", preferences[VisitorDataKey])
    }

    @Test
    fun playbackRepairCannotReplaceUserChoiceWithDefaultChannel() {
        val preferences = mutablePreferencesOf(
            InnerTubeCookieKey to oldSession.cookie!!,
            VisitorDataKey to "visitor",
            DataSyncIdKey to oldSession.dataSyncId!!,
        )
        preferences.applyPlaybackAuthRepair(oldSession, oldSession.copy(dataSyncId = "default||user"))
        assertEquals(oldSession.dataSyncId, preferences[DataSyncIdKey])
    }
}
