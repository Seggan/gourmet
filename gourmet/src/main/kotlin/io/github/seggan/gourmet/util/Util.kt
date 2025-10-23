package io.github.seggan.gourmet.util

import kotlin.random.Random
import kotlin.random.nextULong

fun randomString() = "t${Random.nextULong().toString(16)}"

fun findCloser(
    s: String,
    start: Int,
    openChar: Char,
    closeChar: Char,
    end: Int = s.length,
): Int {
    var openCount = 0
    for (i in start until end) {
        if (s[i] == openChar) {
            openCount++
        } else if (s[i] == closeChar) {
            openCount--
        }
        if (openCount == 0) {
            return i
        }
    }
    return -1
}