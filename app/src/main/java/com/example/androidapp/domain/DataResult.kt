package com.example.androidapp.domain

import kotlinx.coroutines.CancellationException

/**
 * The outcome of an operation that can fail for reasons the caller must handle
 * (ROADMAP F7).
 *
 * Repositories return this instead of throwing, so a failure is a *value* the
 * ViewModel can render. A thrown exception inside a `Flow` chain is especially
 * easy to lose, and "it silently did nothing" is the worst possible response to
 * a set the user just performed.
 */
sealed interface DataResult<out T> {

    data class Success<T>(val data: T) : DataResult<T>

    data class Failure(val error: DataError) : DataResult<Nothing>
}

/** Why an operation failed, in terms the UI can say something useful about. */
sealed interface DataError {

    /** The referenced row does not exist, or was already soft-deleted. */
    data object NotFound : DataError

    /**
     * The caller handed us something we cannot use — a backup file from a newer
     * app, say. [message] is written for the user, not for a log.
     */
    data class Invalid(val message: String) : DataError

    /** The write did not reach the database. */
    data class Storage(val cause: Throwable) : DataError
}

/**
 * Thrown by a repository when the target row is missing; [dataResultOf] turns it
 * into [DataError.NotFound]. Using an exception internally keeps the happy path
 * linear while still producing a typed failure.
 */
class NotFoundException(message: String) : Exception(message)

/**
 * Thrown when input cannot be used, carrying a message meant for the user.
 *
 * Distinct from a storage failure because the two need different words on screen:
 * "your file is from a newer version" is actionable, "could not save" is not.
 */
class InvalidInputException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** `Success(Unit)`, for writes that have nothing to return. */
fun successUnit(): DataResult<Unit> = DataResult.Success(Unit)

/**
 * Runs [block], turning a thrown exception into [DataResult.Failure].
 *
 * [CancellationException] is deliberately rethrown: it is not a failure, it is
 * coroutine cancellation, and swallowing it breaks structured concurrency — the
 * block would keep running after its scope was cancelled.
 *
 * Catching [Throwable] here is the point of the function: this is the boundary
 * where anything the database throws becomes a value the UI can render. The
 * cause is preserved in [DataError.Storage], never discarded.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> dataResultOf(block: suspend () -> T): DataResult<T> =
    try {
        DataResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (
        // NotFoundException is a control-flow signal raised by repositories, not
        // an error to preserve — hence "swallowed" on purpose, and mapped to the
        // typed DataError.NotFound below.
        @Suppress("SwallowedException") notFound: NotFoundException,
    ) {
        DataResult.Failure(DataError.NotFound)
    } catch (invalid: InvalidInputException) {
        // The message is the whole point of this case: it is written for the user.
        DataResult.Failure(DataError.Invalid(invalid.message.orEmpty()))
    } catch (throwable: Throwable) {
        DataResult.Failure(DataError.Storage(throwable))
    }

/** Maps the success value, leaving a failure untouched. */
inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Failure -> this
}

/** The success value, or null when this is a failure. */
fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Success)?.data
