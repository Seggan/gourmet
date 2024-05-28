package io.github.seggan.recipe.compilation

sealed interface Register : AutoCloseable {
    val name: String

    data class Variable(override val name: String) : Register {
        override fun close() {}

        override fun toString(): String = name
    }

    data class Temp(override val name: String, val pool: ArrayDeque<Register>) : Register {
        override fun close() {
            pool.add(this)
        }

        override fun toString(): String = name
    }
}