package com.vplayed.shared.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedConfigTest {

    private fun config(serverUrl: String = "https://api-devgs.vplayed.com/", transportKey: String = "") = SharedConfig(
        environment = ServerEnvironment.DEVELOPMENT,
        serverUrl = serverUrl,
        imageBaseUrl = "https://gsvp-dev.s3.ap-south-1.amazonaws.com/",
        loggingEnabled = true,
        appVersion = "1.0",
        platform = ClientPlatform.IOS,
        transportKey = transportKey,
    )

    @Test
    fun givenTransportKey_whenPrinted_thenItIsMasked() {
        val printed = config(transportKey = "do-not-print").toString()

        assertFalse("do-not-print" in printed)
        assertTrue("transportKey=***" in printed)
        assertTrue("transportKey=unset" in config().toString())
    }

    @Test
    fun givenNonHttpsOrMissingSlash_whenCreating_thenRejected() {
        assertFailsWith<IllegalArgumentException> { config(serverUrl = "http://api-devgs.vplayed.com/") }
        assertFailsWith<IllegalArgumentException> { config(serverUrl = "https://api-devgs.vplayed.com") }
    }

    @Test
    fun givenEnvironmentIds_whenResolving_thenSameIdsAsAndroidBuildTypes() {
        assertEquals(ServerEnvironment.STAGING, ServerEnvironment.fromId("stage"))
        assertEquals(listOf("dev", "qa", "stage", "demo", "production"), ServerEnvironment.entries.map { it.id })
        assertFailsWith<IllegalArgumentException> { ServerEnvironment.fromId("debug") }
    }
}
