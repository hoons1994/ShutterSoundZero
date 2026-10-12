package io.github.hoons1994.shuttersoundzero.update

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseClientTest {
    private val valid = """{"tag_name":"v1.5.14","draft":false,"prerelease":false,"html_url":"https://example.com/evil.apk"}"""

    private class Connection(private val status: Int, private val body: InputStream) :
        HttpURLConnection(URL("https://api.github.com/repos/hoons1994/ShutterSoundZero/releases/latest")) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = body
    }

    @Test fun validReleaseUsesValidatedTagInsteadOfResponseLink() {
        val release = GitHubReleaseClient.parseLatest(valid)
        assertEquals(AppVersion(1, 5, 14), release.version)
        assertEquals("https://github.com/hoons1994/ShutterSoundZero/releases/tag/v1.5.14", release.pageUrl)
    }

    @Test fun draftAndPrereleaseAreRejected() {
        for (field in listOf("draft", "prerelease")) {
            val error = assertThrows(UpdateCheckException::class.java) {
                GitHubReleaseClient.parseLatest(valid.replace("\"$field\":false", "\"$field\":true"))
            }
            assertEquals(UpdateCheckFailure.INVALID_RESPONSE, error.reason)
        }
    }

    @Test fun malformedMissingOrUnexpectedFieldsAreRejected() {
        for (body in listOf("broken", "{}", "[]", valid.replace("v1.5.14", "v1.5.14-beta"),
            valid.replace("\"draft\":false", "\"draft\":\"false\""), valid.replace("v1.5.14", "https://evil.example"))) {
            val error = assertThrows(UpdateCheckException::class.java) { GitHubReleaseClient.parseLatest(body) }
            assertEquals(body, UpdateCheckFailure.INVALID_RESPONSE, error.reason)
        }
    }

    @Test fun successfulRequestHasTimeoutsAndClosesResponseAndConnection() = runTest {
        var closed = false
        val stream = object : ByteArrayInputStream(valid.toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        val connection = Connection(200, stream)
        assertEquals("v1.5.14", GitHubReleaseClient { connection }.fetchLatest().tag)
        assertTrue(closed)
        assertTrue(connection.disconnected)
        assertEquals(8_000, connection.connectTimeout)
        assertEquals(8_000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals("application/vnd.github+json", connection.getRequestProperty("Accept"))
        assertNull(connection.getRequestProperty("Authorization"))
    }

    @Test fun httpFailuresRemainDistinctAndAlwaysDisconnect() = runTest {
        for ((status, expected) in listOf(403 to UpdateCheckFailure.RATE_LIMITED,
            429 to UpdateCheckFailure.RATE_LIMITED, 404 to UpdateCheckFailure.NO_RELEASE,
            500 to UpdateCheckFailure.SERVER, 302 to UpdateCheckFailure.SERVER)) {
            val connection = Connection(status, ByteArrayInputStream(ByteArray(0)))
            try {
                GitHubReleaseClient { connection }.fetchLatest()
                fail("expected HTTP $status failure")
            } catch (error: UpdateCheckException) {
                assertEquals(expected, error.reason)
                assertTrue(connection.disconnected)
            }
        }
    }

    @Test fun networkReadFailureIsNotSuccessfulEmptyResponse() = runTest {
        val connection = Connection(200, object : InputStream() {
            override fun read(): Int = throw IOException("disconnected")
        })
        try {
            GitHubReleaseClient { connection }.fetchLatest()
            fail("expected failure")
        } catch (error: UpdateCheckException) {
            assertEquals(UpdateCheckFailure.NETWORK, error.reason)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun oversizedResponseIsRejectedAndDisconnected() = runTest {
        val connection = Connection(200, ByteArrayInputStream(ByteArray(GitHubReleaseClient.MAX_RESPONSE_BYTES + 1)))
        try {
            GitHubReleaseClient { connection }.fetchLatest()
            fail("expected failure")
        } catch (error: UpdateCheckException) {
            assertEquals(UpdateCheckFailure.INVALID_RESPONSE, error.reason)
            assertTrue(connection.disconnected)
        }
    }
}
