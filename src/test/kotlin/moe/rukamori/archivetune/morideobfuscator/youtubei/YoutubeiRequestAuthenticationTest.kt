package moe.rukamori.archivetune.morideobfuscator.youtubei

import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class YoutubeiRequestAuthenticationTest {
    private val cookie = "SAPISID=synthetic-cookie"
    private fun request(url: String = "https://www.youtube.com/youtubei/v1/player") =
        Request.Builder().url(url).header("Cookie", cookie).build()

    @Test fun personalIdentityDoesNotAlterCookieSignatureOrDelegate() {
        val result = YoutubeiRequestAuthentication.fromSession(cookie, "personal||").applyTo(request())
        val signature = result.header("Authorization")!!.removePrefix("SAPISIDHASH ")
        val timestamp = signature.substringBefore('_')
        val hash = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp synthetic-cookie https://www.youtube.com".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("${timestamp}_$hash", signature)
        assertNull(result.header("X-Goog-PageId"))
        assertEquals("0", result.header("X-Goog-AuthUser"))
    }

    @Test fun brandChannelUsesPageHeaderAndTheSameStandardSignature() {
        val result = YoutubeiRequestAuthentication.fromSession(cookie, "brand||personal").applyTo(request())
        assertEquals("brand", result.header("X-Goog-PageId"))
        assertFalse(result.header("Authorization")!!.endsWith("_u"))
    }

    @Test fun credentialsAreNeverAttachedToOtherHostsOrAnonymousRequests() {
        val auth = YoutubeiRequestAuthentication.fromSession(cookie, "brand||personal")
        val external = Request.Builder().url("https://example.com/youtubei/v1/player").build()
        assertSame(external, auth.applyTo(external))
        val anonymous = request().newBuilder().removeHeader("Cookie").build()
        assertSame(anonymous, auth.applyTo(anonymous))
        val stream = request("https://rr1.googlevideo.com/videoplayback")
        assertSame(stream, auth.applyTo(stream))
    }
}
