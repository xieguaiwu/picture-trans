package com.xieguiawu.picturetrans.util

import java.io.FilterInputStream
import java.io.InputStream

/** 统计已读取字节数的流包装，用于传输进度上报。 */
class CountingInputStream(
    delegate: InputStream,
    private val onBytes: (Int) -> Unit,
) : FilterInputStream(delegate) {

    override fun read(): Int {
        val r = super.read()
        if (r >= 0) onBytes(1)
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        if (r > 0) onBytes(r)
        return r
    }

    override fun skip(n: Long): Long {
        val s = super.skip(n)
        if (s > 0) onBytes(s.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return s
    }
}
