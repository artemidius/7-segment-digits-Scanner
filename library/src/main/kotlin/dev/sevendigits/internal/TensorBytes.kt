package dev.sevendigits.internal

internal fun floatTensorBytes(vararg dimensions: Int): Int {
    var bytes = 4L
    for (dimension in dimensions) {
        require(dimension > 0) { "Tensor dimensions must be positive." }
        bytes *= dimension
        require(bytes <= Int.MAX_VALUE) { "Tensor is too large for a byte buffer." }
    }
    return bytes.toInt()
}
