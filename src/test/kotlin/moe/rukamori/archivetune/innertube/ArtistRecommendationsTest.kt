package moe.rukamori.archivetune.innertube

import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistRecommendationsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun parse(prefix: String): SongItem {
        val content = json.decodeFromString<SectionListRenderer.Content>(
            """{"musicCarouselShelfRenderer":{
              "itemSize":"MUSIC_CAROUSEL_SHELF_ITEM_SIZE_LARGE",
              "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Polecane"}]}}},
              "contents":[{"musicTwoRowItemRenderer":{
                "title":{"runs":[{"text":"Variable"}]},
                "subtitle":{"runs":[$prefix
                  {"text":"Jkb & Koszi","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}},
                  {"text":" oraz "},{"text":"Merssaczek"},
                  {"text":" • "},{"text":"2026"}
                ]},
                "navigationEndpoint":{"watchEndpoint":{"videoId":"test-video"}},
                "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                  {"url":"https://example.test/cover.jpg","width":120,"height":120}
                ]}}}
              }}]
            }}""",
        )
        return ArtistPage.fromSectionListRendererContent(content)!!.items.single() as SongItem
    }

    @Test fun recommendedArtistSongsSkipPolishMediaType() {
        assertEquals(listOf("Jkb & Koszi", "Merssaczek"), parse("""{"text":"Utwór"},{"text":" • "},""").artists.map { it.name })
    }

    @Test fun recommendedArtistSongsSkipEnglishMediaType() {
        assertEquals(listOf("Jkb & Koszi", "Merssaczek"), parse("""{"text":"Song"},{"text":" • "},""").artists.map { it.name })
    }

    @Test fun videoCardsWithoutMediaTypeKeepArtists() {
        assertEquals(listOf("Jkb & Koszi", "Merssaczek"), parse("").artists.map { it.name })
    }
}
