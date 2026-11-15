package dev.sevendigits.internal

import dev.sevendigits.RecognitionException
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

internal interface InferenceSession : AutoCloseable {
    val inputWidth: Int
    val inputHeight: Int
    val candidateCount: Int
    fun run(input: ByteBuffer, output: ByteBuffer)
}

internal fun openLiteRtSession(model: ByteBuffer, classCount: Int): InferenceSession {
    val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    try {
        require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1) {
            "Expected exactly one input and one output tensor."
        }
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        val inputShape = input.shape()
        val outputShape = output.shape()
        require(input.dataType() == DataType.FLOAT32 && output.dataType() == DataType.FLOAT32) {
            "Expected float32 input and output tensors."
        }
        require(inputShape.size == 4 && inputShape[0] == 1 && inputShape[3] == 3 &&
            inputShape[1] > 0 && inputShape[2] > 0) { "Expected input [1,height,width,3]." }
        require(outputShape.size == 3 && outputShape[0] == 1 && outputShape[1] == 4 + classCount &&
            outputShape[2] > 0) { "Expected output [1,4+classes,candidates] matching metadata labels." }
        return LiteRtSession(interpreter, inputShape[2], inputShape[1], outputShape[2])
    } catch (failure: Throwable) {
        interpreter.close()
        throw failure
    }
}

private class LiteRtSession(
    private val interpreter: Interpreter,
    override val inputWidth: Int,
    override val inputHeight: Int,
    override val candidateCount: Int,
) : InferenceSession {
    override fun run(input: ByteBuffer, output: ByteBuffer) = interpreter.run(input, output)
    override fun close() = interpreter.close()
}

/** Native calls are allowed to finish before cancellation releases their resources. */
internal suspend fun <T> withInferenceSession(
    open: () -> InferenceSession,
    work: suspend (InferenceSession) -> T,
): T = withContext(Dispatchers.Default) {
    try {
        currentCoroutineContext().ensureActive()
        open().use { session ->
            currentCoroutineContext().ensureActive()
            val result = work(session)
            currentCoroutineContext().ensureActive()
            result
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        throw RecognitionException("Image recognition failed.", failure)
    } catch (failure: LinkageError) {
        currentCoroutineContext().ensureActive()
        throw RecognitionException("LiteRT native runtime is unavailable.", failure)
    }
}
