package com.vplayed.shared

import com.vplayed.shared.core.config.SharedConfig
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.network.AuthTokens
import com.vplayed.shared.core.network.FakeDeviceInfo
import com.vplayed.shared.core.network.InMemoryRequestCredentials
import com.vplayed.shared.core.network.RecordingEngine
import com.vplayed.shared.core.network.testConfig
import com.vplayed.shared.core.platform.NoOpLogger
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedGraphTest {

    private val recorder = RecordingEngine()
    private val credentials = InMemoryRequestCredentials()

    private fun graph(config: SharedConfig = testConfig()) = SharedGraph(
        config,
        PlatformDependencies(
            deviceInfo = FakeDeviceInfo(),
            logger = NoOpLogger,
            engineFactory = { recorder.engine },
            cryptoPrimitives = platformCryptoPrimitives(),
            requestCredentials = credentials,
        ),
    )

    @Test
    fun givenGraph_whenCredentialsChange_thenBackendRequestsCarryThem() = runTest {
        val graph = graph()
        credentials.setAuthTokens(AuthTokens("access-abc"))

        val result = graph.apiCaller.get("mediaview/api/v1/home")

        assertIs<DataResult.Success<*>>(result)
        assertEquals("Bearer access-abc", recorder.last.headers[HttpHeaders.Authorization])
    }

    @Test
    fun givenTransportKey_whenEncrypting_thenDecryptableWithTheSameKey() {
        val graph = graph()

        val cipher = graph.encryptForTransport("""{"email":"user@example.com"}""")

        assertTrue(cipher!!.isNotEmpty())
        assertEquals("""{"email":"user@example.com"}""", graph.transportCipher.decrypt(graph.config.transportKey, cipher))
    }

    @Test
    fun givenNoTransportKey_whenEncrypting_thenNull() {
        val base = testConfig()
        val noKey = SharedConfig(base.environment, base.serverUrl, base.imageBaseUrl, base.loggingEnabled, base.appVersion, base.platform)

        assertNull(graph(noKey).encryptForTransport("{}"))
    }
}
