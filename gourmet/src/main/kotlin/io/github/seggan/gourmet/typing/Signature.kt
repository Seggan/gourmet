package io.github.seggan.gourmet.typing

data class Signature(val name: String, val type: Type.Function) {
    override fun toString(): String = buildString {
        append(name)
        if (type.genericArgs.isNotEmpty()) {
            append("[")
            append(type.genericArgs.joinToString(", "))
            append("]")
        }
        append("(")
        append(type.args.joinToString(", "))
        append("): ")
        append(type.returnType)
    }
}
