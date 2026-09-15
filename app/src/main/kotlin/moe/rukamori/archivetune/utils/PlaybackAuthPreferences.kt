/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.PoTokenGvsKey
import moe.rukamori.archivetune.constants.PoTokenKey
import moe.rukamori.archivetune.constants.PoTokenPlayerKey
import moe.rukamori.archivetune.constants.PoTokenSourceUrlKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube

fun Preferences.toPlaybackAuthState(): PlaybackAuthState {
    val legacyPoToken =
        this[PoTokenKey]
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    return PlaybackAuthState(
        cookie = this[InnerTubeCookieKey],
        visitorData = this[VisitorDataKey],
        dataSyncId = this[DataSyncIdKey],
        poToken = legacyPoToken,
        poTokenGvs = null,
        poTokenGvsSession = null,
        poTokenPlayer = null,
        webClientPoTokenEnabled = legacyPoToken != null,
    ).normalized()
}

fun MutablePreferences.clearPlaybackAuthSession(clearAccountIdentity: Boolean = true) {
    remove(InnerTubeCookieKey)
    remove(VisitorDataKey)
    remove(DataSyncIdKey)
    remove(PoTokenKey)
    remove(PoTokenGvsKey)
    remove(PoTokenPlayerKey)
    remove(PoTokenSourceUrlKey)
    if (clearAccountIdentity) {
        remove(AccountNameKey)
        remove(AccountEmailKey)
        remove(AccountChannelHandleKey)
    }
}

fun MutablePreferences.clearPlaybackLoginContext() {
    remove(DataSyncIdKey)
}

fun PlaybackAuthState.withoutPlaybackLoginContext(): PlaybackAuthState = copy(dataSyncId = null).normalized()

fun MutablePreferences.putLegacyPoToken(value: String?) {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    if (normalized == null) {
        remove(PoTokenKey)
    } else {
        this[PoTokenKey] = normalized
    }
    remove(PoTokenGvsKey)
    remove(PoTokenPlayerKey)
}

suspend fun Context.resetPlaybackLoginContext(): PlaybackAuthState {
    dataStore.edit { preferences ->
        preferences.clearPlaybackLoginContext()
    }
    val authState = dataStore.data.first().toPlaybackAuthState()
    YouTube.authState = authState
    YTPlayerUtils.clearPlaybackAuthCaches()
    return authState
}

suspend fun <T> Context.retryWithoutPlaybackLoginContext(
    block: suspend (PlaybackAuthState) -> Result<T>,
): Result<T> {
    val initialAuthState = YouTube.currentPlaybackAuthState()
    val initialResult = block(initialAuthState)
    if (initialResult.isSuccess) {
        persistPlaybackAuthRepair(initialAuthState, YouTube.currentPlaybackAuthState())
        return initialResult
    }
    val currentAuthState = YouTube.currentPlaybackAuthState()
    if (!shouldRetryWithoutPlaybackLoginContext(initialAuthState, currentAuthState, initialResult.exceptionOrNull())) {
        return initialResult
    }
    // A metadata retry must not publish a temporary account to the UI, sync jobs,
    // or other concurrent playback requests. Keep its credentials request-local.
    return block(currentAuthState.withoutPlaybackLoginContext())
}

private suspend fun Context.persistPlaybackAuthRepair(
    initialAuthState: PlaybackAuthState,
    repairedAuthState: PlaybackAuthState,
) {
    dataStore.edit { preferences ->
        preferences.applyPlaybackAuthRepair(initialAuthState, repairedAuthState)
    }
}

internal fun MutablePreferences.applyPlaybackAuthRepair(
    initialAuthState: PlaybackAuthState,
    repairedAuthState: PlaybackAuthState,
) {
    val persisted = toPlaybackAuthState()
    if (persisted.cookie != initialAuthState.cookie || persisted.dataSyncId != initialAuthState.dataSyncId ||
        persisted.visitorData != initialAuthState.visitorData || repairedAuthState.cookie != initialAuthState.cookie
    ) return
    // A player retry may refresh the same channel's session, never choose another channel.
    val previousChannel = initialAuthState.dataSyncId?.substringBefore("||")
    val repairedChannel = repairedAuthState.dataSyncId?.substringBefore("||")
    if (previousChannel != null && repairedChannel != null && previousChannel != repairedChannel) return
    repairedAuthState.visitorData?.takeIf(String::isNotBlank)?.let { this[VisitorDataKey] = it }
    repairedAuthState.dataSyncId?.takeIf(String::isNotBlank)?.let { this[DataSyncIdKey] = it }
}

internal fun shouldRetryWithoutPlaybackLoginContext(
    initialAuthState: PlaybackAuthState,
    currentAuthState: PlaybackAuthState,
    failure: Throwable?,
): Boolean {
    if (failure !is YTPlayerUtils.InvalidPlaybackLoginContextException) return false
    if (!initialAuthState.hasPlaybackLoginContext) return false
    if (!currentAuthState.hasPlaybackLoginContext) return false
    if (currentAuthState.cookie != initialAuthState.cookie) return false
    if (currentAuthState.dataSyncId != initialAuthState.dataSyncId) return false
    return true
}
