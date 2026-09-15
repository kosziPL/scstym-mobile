/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.PoTokenGvsKey
import moe.rukamori.archivetune.constants.PoTokenKey
import moe.rukamori.archivetune.constants.PoTokenPlayerKey
import moe.rukamori.archivetune.constants.SavedAccountsKey
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.WebClientPoTokenEnabledKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.innertube.utils.hasCompleteYouTubeLoginCookies
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.decodeSavedAccounts
import moe.rukamori.archivetune.utils.encodeSavedAccounts
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeLoginSession(
    val authState: PlaybackAuthState,
    val accountName: String,
    val accountEmail: String,
    val accountChannelHandle: String,
)

class MissingYouTubeDataSyncIdException : IllegalStateException("YouTube DataSyncId is missing")

@Singleton
class YouTubeLoginRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val sessionMutationMutex = Mutex()

        private suspend fun <T> mutateSession(block: suspend () -> T): Result<T> =
            sessionMutationMutex.withLock {
                runCatchingPreservingCancellation {
                    try {
                        block()
                    } catch (failure: Throwable) {
                        // Cancellation can race with a successful disk commit. Always publish
                        // the latest committed session, never an unverified candidate.
                        withContext(NonCancellable) {
                            YouTube.authState = context.dataStore.data.first().toPlaybackAuthState()
                        }
                        throw failure
                    }
                }
            }

        suspend fun completeLogin(
            cookie: String,
            visitorData: String?,
            dataSyncId: String?,
        ): Result<YouTubeLoginSession> =
            withContext(Dispatchers.IO) {
                mutateSession {
                    val normalizedCookie = cookie.trim()
                    check(hasYouTubeLoginCookie(normalizedCookie)) { "YouTube login cookie is missing" }
                    check(hasCompleteYouTubeLoginCookies(normalizedCookie)) { "YouTube login cookies are incomplete" }

                    val initialAuthState =
                        PlaybackAuthState(
                            cookie = normalizedCookie,
                            visitorData = visitorData,
                            dataSyncId = dataSyncId,
                        ).normalized()
                    val resolvedDataSyncId = resolveRequiredDataSyncId(initialAuthState)
                    val resolvedAuthState = initialAuthState.copy(dataSyncId = resolvedDataSyncId).normalized()
                    val accountInfo = YouTube.accountInfo(resolvedAuthState).getOrThrow()

                    persistLoginSession(
                        authState = resolvedAuthState,
                        accountInfo = accountInfo,
                    )
                    YouTube.authState = context.dataStore.data.first().toPlaybackAuthState()

                    YouTubeLoginSession(
                        authState = resolvedAuthState,
                        accountName = accountInfo.name,
                        accountEmail = accountInfo.email.orEmpty(),
                        accountChannelHandle = accountInfo.channelHandle.orEmpty(),
                    )
                }
            }

        suspend fun switchSavedAccount(account: SavedAccount): Result<PlaybackAuthState> =
            withContext(Dispatchers.IO) {
                mutateSession {
                    check(hasYouTubeLoginCookie(account.innerTubeCookie)) { "Saved account login cookie is missing" }
                    check(hasCompleteYouTubeLoginCookies(account.innerTubeCookie)) {
                        "Saved account login cookies are incomplete"
                    }

                    val initialAuthState =
                        PlaybackAuthState(
                            cookie = account.innerTubeCookie,
                            visitorData = account.visitorData,
                            dataSyncId = account.dataSyncId,
                        ).normalized()
                    val resolvedDataSyncId = resolveRequiredDataSyncId(initialAuthState)
                    val resolvedAuthState = initialAuthState.copy(dataSyncId = resolvedDataSyncId).normalized()
                    val verifiedInfo = YouTube.accountInfo(resolvedAuthState).getOrThrow()

                    context.dataStore.edit { preferences ->
                        preferences[InnerTubeCookieKey] = account.innerTubeCookie
                        account.visitorData
                            .normalizeAuthValue()
                            ?.let { preferences[VisitorDataKey] = it }
                            ?: preferences.remove(VisitorDataKey)
                        preferences[DataSyncIdKey] = resolvedDataSyncId
                        preferences[AccountNameKey] = verifiedInfo.name
                        preferences[AccountEmailKey] = account.email
                        preferences[AccountChannelHandleKey] = verifiedInfo.channelHandle.orEmpty()
                        preferences.remove(PoTokenKey)
                        preferences.remove(PoTokenGvsKey)
                        preferences.remove(PoTokenPlayerKey)
                        preferences[WebClientPoTokenEnabledKey] = false
                        preferences[YtmSyncKey] = account.ytmSync
                        preferences[SelectedYtmPlaylistsKey] = account.selectedYtmPlaylists

                        val savedAccounts = decodeSavedAccounts(preferences[SavedAccountsKey].orEmpty())
                        val repairedAccounts =
                            savedAccounts.map { savedAccount ->
                                if (savedAccount.id == account.id && savedAccount.dataSyncId != resolvedDataSyncId) {
                                    savedAccount.copy(dataSyncId = resolvedDataSyncId)
                                } else {
                                    savedAccount
                                }
                            }
                        if (repairedAccounts != savedAccounts) {
                            preferences[SavedAccountsKey] = encodeSavedAccounts(repairedAccounts)
                        }
                    }

                    context.dataStore.data
                        .first()
                        .toPlaybackAuthState()
                        .also { YouTube.authState = it }
                }
            }

        suspend fun switchAccountChannel(
            dataSyncId: String,
            email: String?,
        ): Result<PlaybackAuthState> = withContext(Dispatchers.IO) {
            mutateSession {
                val previous = context.dataStore.data.first().toPlaybackAuthState()
                val resolvedIdentity = resolveRequiredDataSyncId(previous.copy(dataSyncId = dataSyncId))
                val verified = YouTube.accountInfo(previous.copy(dataSyncId = resolvedIdentity)).getOrThrow()
                context.dataStore.persistYouTubeChannel(resolvedIdentity, verified.name, verified.email ?: email, verified.channelHandle.orEmpty(), previous.cookie) {
                    YouTube.authState = it
                }
            }
        }

        /** Repair the old personal "gaia||" marker without changing the selected channel. */
        suspend fun repairStoredAccount(expected: PlaybackAuthState): Result<PlaybackAuthState> =
            withContext(Dispatchers.IO) {
                mutateSession {
                    val current = context.dataStore.data.first().toPlaybackAuthState()
                    if (current.cookie != expected.cookie || current.dataSyncId != expected.dataSyncId) return@mutateSession current
                    val identity = resolveRequiredDataSyncId(current)
                    if (identity == current.dataSyncId) return@mutateSession current
                    val repaired = current.copy(dataSyncId = identity)
                    val info = YouTube.accountInfo(repaired).getOrThrow()
                    val committed = context.dataStore.edit { preferences ->
                        val latest = preferences.toPlaybackAuthState()
                        check(latest.cookie == current.cookie && latest.dataSyncId == current.dataSyncId) {
                            "Account changed while repairing its identity"
                        }
                        preferences[DataSyncIdKey] = identity
                        preferences[AccountNameKey] = info.name
                        preferences[AccountChannelHandleKey] = info.channelHandle.orEmpty()
                        preferences.remove(PoTokenKey)
                        preferences.remove(PoTokenGvsKey)
                        preferences.remove(PoTokenPlayerKey)
                        val saved = decodeSavedAccounts(preferences[SavedAccountsKey].orEmpty())
                        preferences[SavedAccountsKey] = encodeSavedAccounts(saved.map {
                            if (it.innerTubeCookie == current.cookie && it.dataSyncId == current.dataSyncId) it.copy(dataSyncId = identity) else it
                        })
                    }.toPlaybackAuthState()
                    YouTube.authState = committed
                    committed
                }
            }

        private suspend fun persistLoginSession(
            authState: PlaybackAuthState,
            accountInfo: AccountInfo,
        ) {
            val dataSyncId = authState.dataSyncId ?: throw MissingYouTubeDataSyncIdException()
            context.dataStore.edit { preferences ->
                preferences[InnerTubeCookieKey] = authState.cookie.orEmpty()
                authState.visitorData
                    ?.let { preferences[VisitorDataKey] = it }
                    ?: preferences.remove(VisitorDataKey)
                preferences[DataSyncIdKey] = dataSyncId
                preferences[AccountNameKey] = accountInfo.name
                preferences[AccountEmailKey] = accountInfo.email.orEmpty()
                preferences[AccountChannelHandleKey] = accountInfo.channelHandle.orEmpty()
                preferences.remove(PoTokenKey)
                preferences.remove(PoTokenGvsKey)
                preferences.remove(PoTokenPlayerKey)
                preferences[WebClientPoTokenEnabledKey] = false
            }
        }

        private suspend fun resolveRequiredDataSyncId(authState: PlaybackAuthState): String {
            val channels = YouTube.accountChannels(authState).getOrThrow()
            val candidate = authState.dataSyncId.normalizeDataSyncId()
            val selected = if (candidate == null) {
                channels.firstOrNull { it.isSelected }
            } else {
                channels.firstOrNull { it.dataSyncId == candidate }
                    ?: channels.firstOrNull { it.dataSyncId.substringBefore("||") == candidate.substringBefore("||") }
            }
            return selected?.dataSyncId ?: throw MissingYouTubeDataSyncIdException()
        }

    }

class CompleteYouTubeLoginUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(
            cookie: String,
            visitorData: String?,
            dataSyncId: String?,
        ): Result<YouTubeLoginSession> =
            repository.completeLogin(
                cookie = cookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
            )
    }

class SwitchSavedYouTubeAccountUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(account: SavedAccount): Result<PlaybackAuthState> = repository.switchSavedAccount(account)
    }

private suspend inline fun <T> runCatchingPreservingCancellation(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        Result.failure(throwable)
    }

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? = PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
