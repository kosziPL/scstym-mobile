package moe.rukamori.archivetune.auth

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.*
import moe.rukamori.archivetune.innertube.utils.completed

/** Opt-in device checks. Only switchPersistedChannel mutates preferences; no credentials are logged. */
class AccountSessionDeviceTest {
    @Test fun verifyCompletePlaylistQueue(): Unit = runBlocking {
        val playlistId = InstrumentationRegistry.getArguments().getString("playlistId")
        assumeTrue(!playlistId.isNullOrBlank())
        val page = YouTube.playlist(playlistId!!).completed().getOrThrow()
        assertTrue("Choose a non-empty playlist", page.songs.isNotEmpty())
        val last = page.songs.last()
        val endpoint = moe.rukamori.archivetune.innertube.models.WatchEndpoint(
            playlistId = playlistId, videoId = last.id, playlistSetVideoId = last.setVideoId,
        )
        val expected = page.songs.map { it.id }
        val queue = moe.rukamori.archivetune.playback.queues.YouTubeQueue.playlist(endpoint).getInitialStatus()
        assertEquals("Queue must contain every playlist entry in order", expected, queue.items.map { it.mediaId })
        val shuffled = moe.rukamori.archivetune.playback.queues.YouTubeQueue.playlist(endpoint, shuffle = true).getInitialStatus()
        assertEquals("Shuffle must preserve all entries, including repeats", expected.groupingBy { it }.eachCount(), shuffled.items.groupingBy { it.mediaId }.eachCount())
        Log.i(TAG, "verified complete playlist queue and shuffle: ${expected.size} entries")
    }

    @Test fun switchPersistedChannel(): Unit = runBlocking {
        val index = InstrumentationRegistry.getArguments().getString("switchChannelIndex")?.toIntOrNull()
        assumeTrue(index != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.dataStore.data.first().toPlaybackAuthState()
        val channels = YouTube.accountChannels(before).getOrThrow()
        Log.i(TAG, "original channel index=${channels.indexOfFirst { it.dataSyncId == before.dataSyncId }}")
        val selected = channels[index!!]
        val committed = YouTubeLoginRepository(context).switchAccountChannel(selected.dataSyncId, null).getOrThrow()
        assertEquals(selected.dataSyncId, committed.dataSyncId)
        assertEquals(selected.dataSyncId, context.dataStore.data.first().toPlaybackAuthState().dataSyncId)
        assertEquals(selected.name, YouTube.accountInfo(committed).getOrThrow().name)
        Log.i(TAG, "persisted channel[$index] and verified backend identity")
    }

    @Test fun inspectChannelShape(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("inspectShape") == "true")
        val saved = InstrumentationRegistry.getInstrumentation().targetContext.dataStore.data.first().toPlaybackAuthState()
        val client = InnerTube().apply { applyAuthState(saved.copy(dataSyncId = null)) }
        val response = Json.parseToJsonElement(client.accountChannels(YouTubeClient.WEB.copy(loginSupported = true, supportsCookieAuthentication = true)).bodyAsText())
        val aliases = mutableMapOf<String, String>()
        fun sanitize(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.mapValues { sanitize(it.value) })
            is JsonArray -> JsonArray(value.map(::sanitize))
            is JsonPrimitive -> if (!value.isString) value else JsonPrimitive(value.content.split("||").joinToString("||") { part ->
                if (part.isBlank()) "" else aliases.getOrPut(part) { "value${aliases.size}" }
            })
        }
        fun visit(value: JsonElement) {
            when (value) {
                is JsonObject -> {
                    value["mainAppWebResponseContext"]?.let { Log.i(TAG, "responseContext=${sanitize(it)}") }
                    value["accountItemRenderer"]?.let { Log.i(TAG, "renderer=${sanitize(it)}") }
                    value.values.forEach(::visit)
                }
                is JsonArray -> value.forEach(::visit)
                else -> Unit
            }
        }
        visit(response)
    }

    @Test fun verifyPlaybackForEveryChannel(): Unit = runBlocking {
        val mediaId = InstrumentationRegistry.getArguments().getString("mediaId")
        assumeTrue(!mediaId.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = context.dataStore.data.first().toPlaybackAuthState()
        val channels = YouTube.accountChannels(saved).getOrThrow()
        assertTrue(channels.isNotEmpty())
        val repository = moe.rukamori.archivetune.playback.stream.YoutubeiStreamRepository(context)
        val http = okhttp3.OkHttpClient.Builder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
        try {
            channels.forEachIndexed { index, channel ->
                val session = saved.copy(dataSyncId = channel.dataSyncId)
                val tokens = moe.rukamori.archivetune.utils.YTPlayerUtils.ensureYoutubeiPoTokensForPlayback(mediaId!!, session)
                val stream = repository.resolve(moe.rukamori.archivetune.playback.stream.AudioStreamRequest(
                    mediaId = mediaId,
                    quality = moe.rukamori.archivetune.constants.AudioQuality.HIGH,
                    networkMetered = false,
                    purpose = moe.rukamori.archivetune.playback.stream.StreamPurpose.PLAYBACK,
                    authState = tokens,
                    requiresSongMetadata = true,
                ))
                assertTrue("Missing song metadata for channel $index", !stream.title.isNullOrBlank())
                val request = okhttp3.Request.Builder().url(stream.url).apply {
                    stream.requestHeaders.forEach { (name, value) -> header(name, value) }
                    header("Range", "bytes=0-1023")
                }.build()
                http.newCall(request).execute().use { response ->
                    assertTrue("Audio request failed for channel $index, status=${response.code}", response.isSuccessful)
                    assertTrue("Audio stream is empty", response.body!!.byteStream().read() >= 0)
                }
                Log.i(TAG, "verified channel[$index] song metadata and audio bytes")
            }
        } finally {
            repository.invalidateSessions()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test fun verifyPersistedSessionAndChannels(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySession") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = context.dataStore.data.first().toPlaybackAuthState()
        assertTrue("A logged-in test device is required", saved.hasLoginCookie)
        val channels = YouTube.accountChannels(saved).getOrThrow()
        assertTrue("The account must expose at least one channel", channels.isNotEmpty())
        assertTrue("Persisted identity must match a real channel", channels.any { it.dataSyncId == saved.dataSyncId })
        InstrumentationRegistry.getArguments().getString("expectedChannelIndex")?.toIntOrNull()?.let { index ->
            assertEquals("Channel must survive a new process", channels[index].dataSyncId, saved.dataSyncId)
        }
        channels.forEachIndexed { index, channel ->
            val info = YouTube.accountInfo(saved.copy(dataSyncId = channel.dataSyncId)).getOrThrow()
            assertEquals("Wrong backend identity for channel $index", channel.name, info.name)
            if (!channel.channelHandle.isNullOrBlank()) assertEquals(channel.channelHandle, info.channelHandle)
            Log.i(TAG, "verified channel[$index] backend identity matches")
        }
        val client = InnerTube().apply { applyAuthState(saved) }
        assertEquals(200, client.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_home", setLogin = true).status.value)
        assertEquals("Read-only channel checks must not switch the live session", saved.dataSyncId, context.dataStore.data.first().toPlaybackAuthState().dataSyncId)
    }

    companion object { private const val TAG = "AccountSessionDeviceTest" }
}
