package com.vplayed.shared.session

import com.vplayed.shared.auth.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionCodecTest {

    @Test
    fun givenSession_whenEncodedAndDecoded_thenRoundTrips() {
        val session = testSession()

        assertEquals(session, SessionCodec.decode(SessionCodec.encode(session)))
        assertEquals(testSession(profileId = null, isGuest = true), SessionCodec.decode(SessionCodec.encode(testSession(profileId = null, isGuest = true))))
    }

    @Test
    fun givenSession_whenEncoded_thenFormatOneWithoutNulls() {
        val encoded = SessionCodec.encode(testSession(profileId = null))

        assertTrue(encoded.startsWith("""{"format":1,"accessToken":"access-1","""), encoded)
        assertTrue("profileId" !in encoded && "null" !in encoded, encoded)
    }

    @Test
    fun givenAndroidJpUserSession_whenDecoded_thenSameUserAndTokens() {
        // What AndroidJP's DataStoreSessionRepository stored (kotlinx.serialization, explicitNulls = false).
        val legacy = """{"userId":"u1","email":"user@example.com","displayName":"Sam","accessToken":"access-1","refreshToken":"refresh-1","profileId":"profile-1"}"""

        assertEquals(
            AuthSession(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                userId = "u1",
                uniqueUserId = null,
                email = "user@example.com",
                phone = null,
                firstName = "Sam",
                profileId = "profile-1",
                isGuest = false,
            ),
            SessionCodec.decode(legacy),
        )
    }

    @Test
    fun givenVPlayedAuthCredentials_whenDecoded_thenSameUserAndTokens() {
        // What VPlayed's KeychainCredentialStore stored (JSONEncoder; nil optionals are left out).
        val signedIn = """{"accessToken":"access-1","refreshToken":"refresh-1","userID":"u1","uniqueUserID":42,"email":"user@example.com","phone":"+15550100","firstName":"Sam","isGuest":false}"""
        val guest = """{"accessToken":"guest-1","uniqueUserID":77,"isGuest":true}"""

        assertEquals(
            AuthSession("access-1", "refresh-1", "u1", 42, "user@example.com", "+15550100", "Sam", profileId = null, isGuest = false),
            SessionCodec.decode(signedIn),
        )
        assertEquals(AuthSession("guest-1", null, null, 77, null, null, null, null, isGuest = true), SessionCodec.decode(guest))
    }

    @Test
    fun givenNoUsableAccessToken_whenDecoded_thenNull() {
        assertNull(SessionCodec.decode("""{"userId":"u1","email":"user@example.com"}"""))
        assertNull(SessionCodec.decode("""{"accessToken":"  "}"""))
        assertNull(SessionCodec.decode("""{"accessToken":null}"""))
    }

    @Test
    fun givenNotASessionObject_whenDecoded_thenNull() {
        assertNull(SessionCodec.decode("{not json"))
        assertNull(SessionCodec.decode("""["access-1"]"""))
        assertNull(SessionCodec.decode(""))
    }
}
