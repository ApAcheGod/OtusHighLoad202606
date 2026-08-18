package ru.otus.highload.app.liquibase

import java.io.Reader

class RowSequenceReader(private val rows: Iterator<String>) : Reader() {

    private val pending = StringBuilder(256)
    private var position = 0
    private var exhausted = false

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        while (position >= pending.length) {
            if (!loadNext()) return -1
        }
        val n = minOf(len, pending.length - position)
        pending.getChars(position, position + n, cbuf, off)
        position += n
        if (position >= pending.length) {
            pending.setLength(0)
            position = 0
        }
        return n
    }

    override fun close() = Unit

    private fun loadNext(): Boolean {
        while (!exhausted) {
            if (!rows.hasNext()) {
                exhausted = true
                return false
            }
            pending.append(rows.next()).append('\n')
            return true
        }
        return false
    }
}
