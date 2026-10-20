package io.github.hoons1994.shuttersoundzero.update

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseClientTest {
    private val valid = """{"tag_name":"v1.5.14","draft":false,"prerelease":false,"html_url":"https://example.com/evil.apk"}"""

    private class Connection(
        private val status: Int,
        private val body: InputStream,
        endpoint: String = GitHubReleaseClient.LATEST_RELEASE_URL,
        private val handshakeFailure: Boolean = false
    ) : HttpsURLConnection(URL(endpoint)) {
        var disconnected = false
        var responseReads = 0
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getResponseCode(): Int {
            responseReads++
            if (handshakeFailure) throw SSLHandshakeException("Untrusted certificate")
            return status
        }
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
        val verifier = connection.hostnameVerifier
        val socketFactory = connection.sslSocketFactory
        assertEquals("v1.5.14", GitHubReleaseClient { connection }.fetchLatest().tag)
        assertTrue(closed)
        assertTrue(connection.disconnected)
        assertEquals(8_000, connection.connectTimeout)
        assertEquals(8_000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals("application/vnd.github+json", connection.getRequestProperty("Accept"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertSame(verifier, connection.hostnameVerifier)
        assertSame(socketFactory, connection.sslSocketFactory)
    }

    @Test fun unexpectedEndpointsAreRejectedBeforeAnyNetworkRequest() = runTest {
        for (endpoint in listOf(
            "http://api.github.com/repos/hoons1994/ShutterSoundZero/releases/latest",
            "https://api.github.com.evil.example/repos/hoons1994/ShutterSoundZero/releases/latest",
            "https://user@api.github.com/repos/hoons1994/ShutterSoundZero/releases/latest",
            GitHubReleaseClient.LATEST_RELEASE_URL + "?redirect=evil",
            GitHubReleaseClient.LATEST_RELEASE_URL + "#fragment",
            GitHubReleaseClient.LATEST_RELEASE_URL.replace("ShutterSoundZero", "other-repo")
        )) {
            val connection = Connection(200, ByteArrayInputStream(valid.toByteArray()), endpoint)
            try {
                GitHubReleaseClient { connection }.fetchLatest()
                fail("Unsafe endpoint must not be requested")
            } catch (error: UpdateCheckException) {
                assertEquals(UpdateCheckFailure.INVALID_RESPONSE, error.reason)
                assertEquals(0, connection.responseReads)
                assertTrue(connection.disconnected)
            }
        }
    }

    @Test fun nonTlsConnectionIsRejectedEvenWhenItsUrlLooksTrusted() = runTest {
        var disconnected = false
        val connection = object : HttpURLConnection(URL(GitHubReleaseClient.LATEST_RELEASE_URL)) {
            override fun connect() = error("Must not connect")
            override fun getResponseCode(): Int = error("Must not request")
            override fun disconnect() { disconnected = true }
            override fun usingProxy() = false
        }
        try {
            GitHubReleaseClient { connection }.fetchLatest()
            fail("HTTPS transport is required")
        } catch (error: UpdateCheckException) {
            assertEquals(UpdateCheckFailure.INVALID_RESPONSE, error.reason)
            assertTrue(disconnected)
        }
    }

    @Test fun failedTlsHandshakeIsNotRetriedWithWeakerVerification() = runTest {
        val connection = Connection(200, ByteArrayInputStream(valid.toByteArray()), handshakeFailure = true)
        try {
            GitHubReleaseClient { connection }.fetchLatest()
            fail("Untrusted certificate must fail")
        } catch (error: UpdateCheckException) {
            assertEquals(UpdateCheckFailure.NETWORK, error.reason)
            assertEquals(1, connection.responseReads)
            assertTrue(connection.disconnected)
        }
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
