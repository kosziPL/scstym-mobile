package moe.rukamori.archivetune.innertube

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AccountChannelsTest {
    @Test
    fun modernPageTokensKeepChannelsDistinctAndPreserveUserSession() {
        val response = Json.parseToJsonElement("""
            {"responseContext":{"mainAppWebResponseContext":{"dataSyncId":"default||user"}},
             "items":[
               {"accountItemRenderer":{"accountName":{"simpleText":"First"},
                 "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                   {"gaiaIdToken":{"gaiaId":"user"}},{"pageIdToken":{"pageId":"page1"}}]}}}},
               {"accountItemRenderer":{"accountName":{"runs":[{"text":"Second"}]},"isSelected":true,
                 "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                   {"gaiaIdToken":{"gaiaId":"user"}},{"pageIdToken":{"pageId":"page2"}}]}}}},
               {"accountItemRenderer":{"accountName":{"simpleText":"Disabled"},"isDisabled":true,
                 "serviceEndpoint":{"pageIdToken":{"pageId":"disabled"}}}}
             ]}
        """.trimIndent())
        val channels = YouTube.parseAccountChannelsResponse(response)
        assertEquals(listOf("Second", "First"), channels.map { it.name })
        assertEquals(listOf("page2||user", "page1||user"), channels.map { it.dataSyncId })
        assertTrue(channels.first().isSelected)
    }

    @Test
    fun explicitDataSyncIdentityStillWorks() {
        val response = Json.parseToJsonElement("""
            {"accountItemRenderer":{"accountName":{"simpleText":"Channel"},
             "serviceEndpoint":{"dataSyncId":"page%7C%7Cuser"}}}
        """.trimIndent())
        assertEquals("page||user", YouTube.parseAccountChannelsResponse(response).single().dataSyncId)
    }
}
