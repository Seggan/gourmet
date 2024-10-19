package io.github.seggan.gourmet.util

import kotlin.random.Random
import kotlin.random.nextULong

fun randomString() = "t${Random.nextULong().toString(16)}"