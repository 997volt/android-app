package com.example.androidapp.domain

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the write-outcome type (ROADMAP F7).
 *
 * The cancellation test is the important one: treating `CancellationException`
 * as an ordinary failure would swallow coroutine cancellation, leaving work
 * running after its scope was torn down.
 */
class DataResultTest {

    @Test
    fun successCarriesTheValue() = runTest {
        val result = dataResultOf { 42 }

        assertEquals(42, result.getOrNull())
        assertTrue(result is DataResult.Success)
    }

    @Test
    fun thrownExceptionBecomesStorageFailure() = runTest {
        val result = dataResultOf<Int> { throw IOException("disk gone") }

        val error = (result as DataResult.Failure).error
        assertTrue(error is DataError.Storage)
        assertEquals("disk gone", (error as DataError.Storage).cause.message)
    }

    @Test
    fun notFoundBecomesNotFound_notStorage() = runTest {
        val result = dataResultOf<Int> { throw NotFoundException("no such session") }

        assertEquals(DataError.NotFound, (result as DataResult.Failure).error)
    }

    @Test
    fun cancellationIsRethrown_neverSwallowed() {
        assertThrows(CancellationException::class.java) {
            runTest {
                dataResultOf<Int> { throw CancellationException("scope cancelled") }
            }
        }
    }

    @Test
    fun mapTransformsSuccessAndPassesFailureThrough() {
        val mapped = DataResult.Success(2).map { it * 21 }
        assertEquals(42, mapped.getOrNull())

        val failure: DataResult<Int> = DataResult.Failure(DataError.NotFound)
        assertEquals(DataError.NotFound, (failure.map { it * 2 } as DataResult.Failure).error)
    }

    @Test
    fun getOrNullIsNullOnFailure() {
        assertEquals(null, DataResult.Failure(DataError.NotFound).getOrNull())
    }
}
