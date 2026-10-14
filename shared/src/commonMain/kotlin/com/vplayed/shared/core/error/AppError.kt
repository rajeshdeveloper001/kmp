package com.vplayed.shared.core.error

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Failures the apps can explain to the user. Shared code converts exceptions with [toAppError] and
 * never lets them escape a public API (an exception crossing into Swift crashes the app).
 * Port of AndroidJP `core/error/AppError.kt`, plus [Timeout] (VPlayed shows its own message) and
 * [Configuration].
 */
sealed interface AppError {
    /** No connection, DNS or TLS failure. Retrying may help. */
    data object Network : AppError

    /** The server did not answer in time. Retrying may help. */
    data object Timeout : AppError

    /** Session missing or expired (HTTP 401). */
    data object Unauthorized : AppError

    /** The server answered with an error status (HTTP or API envelope), with its message when it sent one. */
    data class Server(val code: Int, val message: String? = null) : AppError

    /** The response could not be read into the expected model. */
    data object Parsing : AppError

    /** The build lacks something a request needs (e.g. the transport key); no request was sent. */
    data class Configuration(val reason: String) : AppError

    data class Unknown(val cause: Throwable? = null) : AppError
}

/** An API envelope that reported failure although the HTTP call succeeded. */
class ApiException(val code: Int, override val message: String?) : Exception(message)

const val HTTP_UNAUTHORIZED = 401

fun Throwable.toAppError(): AppError = when (this) {
    is ApiException -> if (code == HTTP_UNAUTHORIZED) AppError.Unauthorized else AppError.Server(code, message)
    // Timeouts first: Ktor's timeout exceptions are IOExceptions too.
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> AppError.Timeout
    is SerializationException -> AppError.Parsing
    is IOException -> AppError.Network
    else -> AppError.Unknown(this)
}

/** Result of a shared call: either data or a typed [AppError]. Shared public APIs return this instead of throwing. */
sealed interface DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>

    data class Failure(val error: AppError) : DataResult<Nothing>
}

/**
 * Runs [block] and converts any failure into [DataResult.Failure]. Coroutine cancellation is
 * rethrown so structured concurrency keeps working.
 */
suspend fun <T> resultOf(block: suspend () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    DataResult.Failure(error.toAppError())
}

/** Transforms the data of a success; a failure passes through. */
inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Failure -> this
}

/** Chains a call that itself returns a result; a failure passes through. */
inline fun <T, R> DataResult<T>.flatMap(transform: (T) -> DataResult<R>): DataResult<R> = when (this) {
    is DataResult.Success -> transform(data)
    is DataResult.Failure -> this
}
