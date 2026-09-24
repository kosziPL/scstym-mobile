package moe.rukamori.archivetune.innertube

import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.pages.RelatedPage
import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedPageTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun parse(metadata: String) = RelatedPage.fromMusicResponsiveListItemRenderer(
        json.decodeFromString<MusicResponsiveListItemRenderer>(
            """{
              "playlistItemData":{"videoId":"test-video"},
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Variable"}]}}},
                $metadata
              ],
              "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                {"url":"https://example.test/cover.jpg","width":120,"height":120}
              ]}}}
            }""",
        ),
    )!!

    private fun column(runs: String) =
        """{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[$runs]}}}"""

    private val artists = """{"text":"Jkb & Koszi","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}},
        {"text":" oraz "},{"text":"Merssaczek"}"""
    private val album = """{"text":"OST","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_album"}}}"""

    @Test fun separateMediaTypeColumnIsNotAnArtist() {
        val song = parse(listOf(column("""{"text":"Utwór"}"""), column(artists), column(album)).joinToString(","))
        assertEquals(listOf("Jkb & Koszi", "Merssaczek"), song.artists.map { it.name })
        assertEquals("MPREb_album", song.album?.id)
    }

    @Test fun combinedSubtitleSkipsMediaTypeAndAlbum() {
        val separator = """{"text":" • "}"""
        val song = parse(column("""{"text":"Song"},$separator,$artists,$separator,$album"""))
        assertEquals(listOf("Jkb & Koszi", "Merssaczek"), song.artists.map { it.name })
        assertEquals("OST", song.album?.name)
    }

    @Test fun originalArtistColumnKeepsUnlinkedFeaturedArtist() {
        val song = parse(column(artists) + "," + column(album))
        assertEquals(listOf("Jkb & Koszi", "Merssaczek"), song.artists.map { it.name })
    }
}
