package com.vplayed.shared.core.interop

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// UnconfinedTestDispatcher stands in for Dispatchers.Main, which needs a running main run loop.
@OptIn(ExperimentalCoroutinesApi::class)
class FlowWatcherTest {

    @Test
    fun givenWatcher_whenStateChanges_thenEveryValueArrivesUntilClosed() = runTest {
        val state = MutableStateFlow(1)
        val watcher = StateFlowWatcher(state, UnconfinedTestDispatcher(testScheduler))
        val received = mutableListOf<Int>()

        val subscription = watcher.watch { received += it }
        state.value = 2
        subscription.close()
        state.value = 3

        assertEquals(listOf(1, 2), received)
        assertEquals(3, watcher.value)
    }

    @Test
    fun givenClosedTwice_whenClosing_thenNoError() = runTest {
        val subscription = FlowWatcher(MutableStateFlow("a"), UnconfinedTestDispatcher(testScheduler)).watch { }

        subscription.close()
        subscription.close()
    }
}
