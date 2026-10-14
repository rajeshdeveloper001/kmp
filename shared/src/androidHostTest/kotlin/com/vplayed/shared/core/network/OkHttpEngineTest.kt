package com.vplayed.shared.core.network

import com.vplayed.shared.PlatformDependencies
import com.vplayed.shared.SharedGraph
import com.vplayed.shared.auth.EmailLoginResult
import com.vplayed.shared.core.config.ClientPlatform
import com.vplayed.shared.core.config.ServerEnvironment
import com.vplayed.shared.core.config.SharedConfig
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.platform.NoOpLogger
import com.vplayed.shared.session.SessionStatus
import com.vplayed.shared.session.SignOutReason
import com.vplayed.shared.session.testSession
import com.vplayed.shared.session.testSessions
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/**
 * The Android engine (OkHttp through `createOkHttpEngine`, as AndroidJP builds it) against a real local HTTP server
 * (KMP-603): what reaches the wire, real timeouts and refused connections, and decision D21 end to
 * end. The unit tests elsewhere use Ktor's MockEngine, which skips the engine itself.
 */
class OkHttpEngineTest {

    private class Recorded(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private class Answer(val status: Int, val body: String, val delayMillis: Long = 0)

    /** One HTTP/1.1 exchange per connection, answered by [handler]; records every request. */
    private class TinyServer(private val handler: (Recorded) -> Answer) : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val requests = CopyOnWriteArrayList<Recorded>()
        val port: Int get() = socket.localPort

        init {
            thread(isDaemon = true) {
                while (true) {
                    val client = try {
                        socket.accept()
                    } catch (_: SocketException) {
                        break
                    }
                    thread(isDaemon = true) { serve(client) }
                }
            }
        }

        private fun serve(client: Socket) = client.use {
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.ISO_8859_1))
            val (method, path) = reader.readLine().orEmpty().split(" ").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }
                .associate { line -> line.substringBefore(":").trim().lowercase() to line.substringAfter(":").trim() }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = CharArray(length).also { var read = 0; while (read < length) read += reader.read(it, read, length - read) }
            val recorded = Recorded(method, path, headers, String(body))
            requests += recorded
            val answer = handler(recorded)
            if (answer.delayMillis > 0) Thread.sleep(answer.delayMillis)
            val bytes = answer.body.toByteArray()
            try {
                client.getOutputStream().apply {
                    write(
                        ("HTTP/1.1 ${answer.status} X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(),
                    )
                    write(bytes)
                    flush()
                }
            } catch (_: SocketException) {
                // The client gave up (timeout test).
            }
        }

        override fun close() = socket.close()
    }

    private var server: TinyServer? = null

    @AfterTest
    fun stop() {
        server?.close()
    }

    private fun serve(handler: (Recorded) -> Answer) = TinyServer(handler).also { server = it }

    /**
     * `SharedConfig` accepts only https server URLs, so the graph keeps one and this OkHttp client
     * (the app's way in: `createOkHttpEngine`) sends its requests in plain HTTP to the local [port].
     */
    private fun localClient(port: Int) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request.newBuilder().url(request.url.newBuilder().scheme("http").host("127.0.0.1").port(port).build()).build())
        }
        .build()

    private fun graph(port: Int, timeoutMillis: Long = 5_000) = SharedGraph(
        SharedConfig(
            environment = ServerEnvironment.DEVELOPMENT,
            serverUrl = "https://api.test.invalid/",
            imageBaseUrl = "https://images.test.invalid/",
            loggingEnabled = false,
            appVersion = "3.0.0",
            platform = ClientPlatform.ANDROID,
            transportKey = "unit-test-passphrase",
            requestTimeoutMillis = timeoutMillis,
            connectTimeoutMillis = timeoutMillis,
        ),
        PlatformDependencies(FakeDeviceInfo(), NoOpLogger, { createOkHttpEngine(localClient(port)) }, platformCryptoPrimitives()),
        testSessions(stored = testSession("access-1", profileId = "profile-1")),
    )

    @Test
    fun givenSessionAndGeoToken_whenRequesting_thenEveryVPlayedHeaderReachesTheServer() = runBlocking {
        val server = serve { Answer(200, """{"statusCode":200,"response":{"content_library":1}}""") }
        val graph = graph(server.port)
        graph.geoStore.updateGeo("geo-xyz", null)

        val result = graph.commonSettingsRepository.load()

        assertIs<DataResult.Success<*>>(result)
        val sent = server.requests.single()
        assertEquals("GET", sent.method)
        assertEquals("/mediaview/api/v1/common", sent.path)
        assertEquals("Bearer access-1", sent.headers["authorization"])
        assertEquals("profile-1", sent.headers["profile_id"])
        assertEquals("geo-xyz", sent.headers["x-bearer-token"])
        assertEquals("device-123", sent.headers["device_id"])
        assertEquals("android", sent.headers["device_type"])
        assertEquals("mobile", sent.headers["x-request-type"])
        assertEquals("3.0.0", sent.headers["app_version"])
        assertEquals("ta", sent.headers["x-language-code"])
    }

    @Test
    fun givenEmailLogin_whenSent_thenTheServerGetsAnEncryptedJsonBodyItCanDecrypt() = runBlocking {
        val server = serve { Answer(200, """{"statusCode":200,"message":"Code sent"}""") }
        val graph = graph(server.port)

        val result = graph.authRepository.requestEmailLogin("user@example.com")

        assertIs<EmailLoginResult.CodeSent>(result)
        val sent = server.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("application/json", sent.headers["content-type"]?.substringBefore(";"))
        val cipherText = Json.parseToJsonElement(sent.body).jsonObject.getValue("data").jsonPrimitive.content
        val plain = graph.transportCipher.decrypt("unit-test-passphrase", cipherText)
        assertEquals("user@example.com", Json.parseToJsonElement(plain!!).jsonObject.getValue("email").jsonPrimitive.content)
    }

    @Test
    fun givenSlowServer_whenTheRequestTimeoutPasses_thenTimeout() = runBlocking {
        val server = serve { Answer(200, """{"statusCode":200,"response":{}}""", delayMillis = 2_000) }

        val result = graph(server.port, timeoutMillis = 300).commonSettingsRepository.load()

        assertEquals(DataResult.Failure(AppError.Timeout), result)
    }

    @Test
    fun givenNothingListening_whenRequesting_thenNetworkFailure() = runBlocking {
        val closedPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

        val result = graph(closedPort).commonSettingsRepository.load()

        assertEquals(DataResult.Failure(AppError.Network), result)
    }

    @Test
    fun givenRealServerAnswers401_whenRequesting_thenTheSessionEnds() = runBlocking {
        val server = serve { Answer(401, """{"statusCode":401,"message":"Token expired"}""") }
        val graph = graph(server.port)

        val result = graph.homeRepository.getBanners()

        assertEquals(DataResult.Failure(AppError.Unauthorized), result)
        assertEquals(SessionStatus.SignedOut(SignOutReason.EXPIRED), graph.sessionManager.status.value)
    }
}
