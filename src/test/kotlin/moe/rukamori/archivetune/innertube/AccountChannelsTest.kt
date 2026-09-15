package moe.rukamori.archivetune.innertube

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AccountChannelsTest {
    @Test
    fun invitationDialogDoesNotBecomeAnAuthenticatedChannel() {
        val response = Json.parseToJsonElement("""
            {"accountItemRenderer":{"accountName":{"simpleText":"Invitation"},
             "serviceEndpoint":{"openPopupAction":{"popup":{"confirmDialogRenderer":{
               "confirmButton":{"buttonRenderer":{"command":{"commandExecutorCommand":{"commands":[
                 {"selectActiveIdentityEndpoint":{"supportedTokens":[
                   {"datasyncIdToken":{"datasyncIdToken":"invited||user"}}]}}
               ]}}}}
             }}}}}}
        """.trimIndent())
        assertTrue(YouTube.parseAccountChannelsResponse(response).isEmpty())
    }

    @Test
    fun serverDataSyncTokensTakePriorityOverGuessedIdentifiers() {
        val response = Json.parseToJsonElement("""
            {"items":[
              {"accountItemRenderer":{"accountName":{"simpleText":"Personal"},
               "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                 {"accountStateToken":{"obfuscatedGaiaId":"personal"}},
                 {"datasyncIdToken":{"datasyncIdToken":"personal||"}}]}}}},
              {"accountItemRenderer":{"accountName":{"simpleText":"Brand"},
               "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                 {"pageIdToken":{"pageId":"brand"}},
                 {"datasyncIdToken":{"datasyncIdToken":"brand||actual-session"}}]}}}}
            ]}
        """.trimIndent())
        assertEquals(listOf("personal", "brand||actual-session"), YouTube.parseAccountChannelsResponse(response).map { it.dataSyncId })
    }

    @Test
    fun personalAccountTokenIsNotDelegatedEvenWhenResponseDataSyncHasTrailingSeparator() {
        val response = Json.parseToJsonElement("""
            {"responseContext":{"mainAppWebResponseContext":{"datasyncId":"personal||"}},
             "actions":[{"updateChannelSwitcherPageAction":{"page":{"channelSwitcherPageRenderer":{"contents":[
               {"accountItemRenderer":{"accountName":{"simpleText":"Personal"},"isSelected":true,
                 "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                   {"accountStateToken":{"obfuscatedGaiaId":"personal"}}]}}}},
               {"accountItemRenderer":{"accountName":{"simpleText":"Brand"},
                 "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                   {"pageIdToken":{"pageId":"brand"}},{"accountStateToken":{"obfuscatedGaiaId":"brand-gaia"}}]}}}}
             ]}}}}]}
        """.trimIndent())
        val channels = YouTube.parseAccountChannelsResponse(response)
        assertEquals(listOf("personal", "brand||personal"), channels.map { it.dataSyncId })
        assertNull(channels[0].dataSyncId.delegatedSessionIdOrNull())
        assertEquals("brand", channels[1].dataSyncId.delegatedSessionIdOrNull())
    }

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
    @Test
    fun pageTokenWithoutResponseSessionStillSelectsChannelInRequestsAfterReload() {
        val response = Json.parseToJsonElement("""
            {"accountItemRenderer":{"accountName":{"simpleText":"Brand channel"},
             "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
               {"pageIdToken":{"pageId":"brand-page"}}]}}}}
        """.trimIndent())
        val selected = YouTube.parseAccountChannelsResponse(response).single()
        val persisted = PlaybackAuthState(dataSyncId = selected.dataSyncId).normalized().dataSyncId
        val restored = PlaybackAuthState(dataSyncId = persisted).normalized()
        val request = moe.rukamori.archivetune.innertube.models.YouTubeClient.WEB
            .copy(supportsCookieAuthentication = true)
            .toContext(moe.rukamori.archivetune.innertube.models.YouTubeLocale("US", "en"), null, restored.dataSyncId)
        val requestJson = Json.encodeToString(moe.rukamori.archivetune.innertube.models.Context.serializer(), request)
        assertTrue(requestJson, requestJson.contains("\"onBehalfOfUser\":\"brand-page\""))
        assertEquals("brand-page||", restored.dataSyncId)
    }

    @Test
    fun explicitDelegationRemainsDifferentFromPersonalGoogleSession() {
        val delegated = Json.parseToJsonElement("""
            {"accountItemRenderer":{"accountName":{"simpleText":"Brand"},
             "serviceEndpoint":{"onBehalfOfUser":"page"}}}
        """.trimIndent())
        assertEquals("page||", YouTube.parseAccountChannelsResponse(delegated).single().dataSyncId)
        assertEquals("page", "page||".delegatedSessionIdOrNull())
        assertNull("personal-user".delegatedSessionIdOrNull())
        assertEquals("personal-user", PlaybackAuthState(dataSyncId = "||personal-user").normalized().dataSyncId)
    }

    @Test
    fun latePlaybackRepairCannotReplaceAnotherChannelInLiveClient() {
        val original = YouTube.authState
        try {
            val beforeSwitch = PlaybackAuthState(cookie = "SAPISID=synthetic", dataSyncId = "old||user")
            val selected = beforeSwitch.copy(dataSyncId = "new||user")
            YouTube.authState = selected
            assertFalse(YouTube.updateAuthStateIfSessionMatches(beforeSwitch, beforeSwitch.copy(visitorData = "late")))
            assertEquals(selected, YouTube.authState)
        } finally {
            YouTube.authState = original
        }
    }

}
