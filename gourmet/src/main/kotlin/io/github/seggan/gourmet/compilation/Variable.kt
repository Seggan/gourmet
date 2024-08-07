package io.github.seggan.gourmet.compilation

data class Variable(val name: String, val mapped: List<String>) {
    val size by mapped::size
}