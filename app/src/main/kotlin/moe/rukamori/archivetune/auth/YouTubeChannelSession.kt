package moe.rukamori.archivetune.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.PoTokenKey
import moe.rukamori.archivetune.constants.PoTokenGvsKey
import moe.rukamori.archivetune.constants.PoTokenPlayerKey
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.utils.toPlaybackAuthState

/** Publish exactly the session committed to disk, rather than a UI-only account selection. */
internal suspend fun DataStore<Preferences>.persistYouTubeChannel(
    dataSyncId: String,
    name: String,
    email: String?,
    handle: String,
    expectedCookie: String?,
    applySession: (PlaybackAuthState) -> Unit,
): PlaybackAuthState {
    val identity = requireNotNull(PlaybackAuthState(dataSyncId = dataSyncId).normalized().dataSyncId)
    val committed = edit { preferences ->
        val current = preferences.toPlaybackAuthState()
        check(current.hasLoginCookie) { "Cannot switch channel without a Google login" }
        check(current.cookie == expectedCookie) { "Google account changed while selecting the channel" }
        preferences[DataSyncIdKey] = identity
        preferences[AccountNameKey] = name
        preferences[AccountChannelHandleKey] = handle
        email?.takeIf(String::isNotBlank)?.let { preferences[AccountEmailKey] = it }
        preferences.remove(PoTokenKey)
        preferences.remove(PoTokenGvsKey)
        preferences.remove(PoTokenPlayerKey)
    }.toPlaybackAuthState()
    applySession(committed)
    return committed
}
