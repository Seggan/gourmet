package io.github.seggan.gourmet

class Pool<T>(private val factory: () -> T) {

    private val pool = ArrayDeque<T>()

    fun <R> use(block: (T) -> R) {
        val instance = if (pool.isEmpty()) factory() else pool.removeLast()
        block(instance)
        pool.add(instance)
    }
}