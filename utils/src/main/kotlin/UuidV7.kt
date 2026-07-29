package ru.otus.highload.utils

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

fun uuidV7(): UUID {
    val random = ThreadLocalRandom.current()
    val time = System.currentTimeMillis()
    val mostSigBits = (time shl 16) or (0x7L shl 12) or (random.nextLong() and 0xFFFL)
    val leastSigBits = (0x2L shl 62) or (random.nextLong() and 0x3FFFFFFFFFFFFFFFL)
    return UUID(mostSigBits, leastSigBits)
}
