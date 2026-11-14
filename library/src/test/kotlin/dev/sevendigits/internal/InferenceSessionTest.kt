package dev.sevendigits.internal

import dev.sevendigits.RecognitionException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InferenceSessionTest {
    @Test
    fun closesOnSuccessAndWrapsOperationalFailureWithItsCause() = runBlocking {
        val first = FakeSession()
        assertEquals(42, withInferenceSession({ first }) { 42 })
        assertEquals(1, first.closed.get())
        val second = FakeSession()
        val failure = IllegalStateException("native failure")
        try {
            withInferenceSession({ second }) { throw failure }
            fail("Expected failure")
        } catch (caught: RecognitionException) {
            // Coroutine stack-trace recovery may add a copy of the wrapping exception.
            assertSame(failure, generateSequence<Throwable>(caught) { it.cause }.last())
        }
        assertEquals(1, second.closed.get())
    }

    @Test
    fun cancellationWaitsForBlockingInvocationThenClosesBeforeCompletion() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val session = FakeSession()
        val job = async {
            withInferenceSession({ session }) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                "must not be returned after cancellation"
            }
        }
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(10, TimeUnit.SECONDS)) }
            job.cancel()
            assertEquals(0, session.closed.get()) // Never close an active native invocation.
            assertFalse(job.isCompleted)
        } finally {
            release.countDown()
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, session.closed.get())
        try { job.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }

    @Test
    fun alreadyCancelledCallDoesNotOpenSession() = runBlocking {
        val opened = AtomicInteger()
        val job = launch {
            currentCoroutineContext().cancel()
            withInferenceSession({ opened.incrementAndGet(); FakeSession() }) { }
        }
        job.join()
        assertEquals(0, opened.get())
    }

    private class FakeSession : InferenceSession {
        val closed = AtomicInteger()
        override val inputWidth = 1
        override val inputHeight = 1
        override val candidateCount = 1
        override fun run(input: ByteBuffer, output: ByteBuffer) = Unit
        override fun close() { closed.incrementAndGet() }
    }
}
