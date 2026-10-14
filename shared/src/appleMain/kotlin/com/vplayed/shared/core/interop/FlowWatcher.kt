package com.vplayed.shared.core.interop

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Stops a [FlowWatcher.watch] subscription. Safe to call more than once. */
fun interface Closeable {
    fun close()
}

/**
 * Lets Swift observe a Kotlin [Flow]: Swift cannot collect flows, and a Kotlin `StateFlow` is not an
 * `@Observable`. A SwiftUI `@Observable` adapter calls [watch] once, stores the returned [Closeable]
 * and closes it in `deinit` (or when its `.task` is cancelled).
 *
 * Values are delivered on [dispatcher] — the main thread by default — so the adapter can update
 * main-actor state directly. Collection errors are not delivered: shared flows expose failures as
 * state, never as exceptions.
 */
class FlowWatcher<T : Any>(
    private val flow: Flow<T>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    fun watch(onEach: (T) -> Unit): Closeable {
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        scope.launch { flow.collect { onEach(it) } }
        return Closeable { scope.cancel() }
    }
}

/** A watcher that also exposes the current value, for an adapter's initial state. */
class StateFlowWatcher<T : Any>(
    private val stateFlow: StateFlow<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val watcher = FlowWatcher(stateFlow, dispatcher)

    val value: T get() = stateFlow.value

    /** [onEach] receives the current value first, then every change. */
    fun watch(onEach: (T) -> Unit): Closeable = watcher.watch(onEach)
}
