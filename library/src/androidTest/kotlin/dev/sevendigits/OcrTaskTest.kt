package dev.sevendigits

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import dev.sevendigits.internal.awaitCompletion
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrTaskTest {
    @Test fun cancellationWaitsForOcrBeforeReleasingResources() = runBlocking {
        val task = TaskCompletionSource<String>()
        var released = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                task.task.awaitCompletion()
                currentCoroutineContext().ensureActive()
                fail("Cancelled call must not return text")
            } finally { released = true }
        }
        job.cancel()
        assertFalse(job.isCompleted)
        assertFalse(released)
        task.setResult("done")
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(released)
    }

    @Test fun propagatesOcrFailure() = runBlocking {
        val task = TaskCompletionSource<String>()
        val failure = IllegalStateException("failed OCR")
        task.setException(failure)
        try {
            task.task.awaitCompletion()
            fail("Expected original error")
        } catch (caught: IllegalStateException) { assertEquals("failed OCR", caught.message) }
    }
}
