package moe.rukamori.archivetune.innertube.utils

import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import moe.rukamori.archivetune.innertube.pages.PlaylistContinuationPage
import org.junit.Assert.*
import org.junit.Test

class PlaylistCompletionTest {
    private fun song(entry: String) = SongItem("same-video", entry, emptyList(), thumbnail = "", setVideoId = entry)
    private fun page() = PlaylistPage(
        PlaylistItem("PLtest", "Test", null, null, null, null, null, null),
        listOf(song("entry0")), "1", null,
    )

    @Test
    fun loadsPastEmptyPagesAndKeepsRepeatedSongsWithDifferentEntryIds() = runBlocking {
        val result = completePlaylistPage(page()) { token ->
            val index = token.toInt()
            PlaylistContinuationPage(
                songs = if (index in 2..3) emptyList() else listOf(song("entry$index")),
                continuation = if (index < 12) (index + 1).toString() else null,
            )
        }
        assertEquals(11, result.songs.size)
        assertEquals("entry12", result.songs.last().setVideoId)
        assertNull(result.continuation)
    }

    @Test
    fun missingPageDoesNotReportPartialPlaylistAsComplete() = runBlocking {
        val result = runCatching { completePlaylistPage(page()) { null } }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun repeatedContinuationFailsInsteadOfLoopingOrSilentlyTruncating() = runBlocking {
        val result = runCatching {
            completePlaylistPage(page()) { PlaylistContinuationPage(listOf(song("entry1")), "1") }
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
