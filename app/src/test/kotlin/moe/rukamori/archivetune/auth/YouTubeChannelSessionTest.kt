package moe.rukamori.archivetune.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class YouTubeChannelSessionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun committedChannelMatchesLiveSessionAndSurvivesStoreRecreation() = runBlocking {
        val file = File(temporaryFolder.root, "account.preferences_pb")
        val writerJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + writerJob)) { file }
        val cookie = "SAPISID=synthetic"
        try {
            store.edit {
                it[InnerTubeCookieKey] = cookie
                it[VisitorDataKey] = "visitor"
                it[DataSyncIdKey] = "old-page||user"
            }
            var live = PlaybackAuthState.EMPTY
            store.persistYouTubeChannel("new-page||", "New channel", null, "@new", cookie) { live = it }
            assertEquals(store.data.first().toPlaybackAuthState(), live)
            assertEquals("new-page||", live.dataSyncId)
            assertEquals(cookie, live.cookie)
            assertEquals("visitor", live.visitorData)
        } finally {
            writerJob.cancelAndJoin()
        }
        val readerJob = SupervisorJob()
        try {
            val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + readerJob)) { file }
            val preferences = reopened.data.first()
            assertEquals("new-page||", preferences.toPlaybackAuthState().dataSyncId)
            assertEquals("New channel", preferences[AccountNameKey])
        } finally {
            readerJob.cancelAndJoin()
        }
    }

    @Test
    fun delayedChannelSelectionCannotModifyAnotherGoogleAccount() = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            File(temporaryFolder.root, "race.preferences_pb")
        }
        try {
            store.edit {
                it[InnerTubeCookieKey] = "SAPISID=new-google"
                it[DataSyncIdKey] = "new-channel||user"
            }
            var published = false
            val result = runCatching {
                store.persistYouTubeChannel("old-channel||", "Old", null, "", "SAPISID=old-google") { published = true }
            }
            assertTrue(result.isFailure)
            assertFalse(published)
            assertEquals("new-channel||user", store.data.first().toPlaybackAuthState().dataSyncId)
        } finally {
            job.cancelAndJoin()
        }
    }
}
