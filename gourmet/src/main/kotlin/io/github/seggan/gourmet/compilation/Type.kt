package io.github.seggan.gourmet.compilation

sealed interface Type {

    val tname: String
    val size: Int

    enum class Primitive(override val tname: String) : Type {
        NUMBER("Number"),
        BOOLEAN("Boolean"),
        CHAR("Char");

        override val size = 1

        override fun fillGeneric(generic: Type, type: Type): Type {
            return this
        }
    }

    data object Any : Type {
        override val tname = "Any"
        override val size: Int
            get() = throw UnsupportedOperationException("Any type has no size")

        override fun isAssignableTo(other: Type): Boolean {
            return false
        }

        override fun fillGeneric(generic: Type, type: Type): Type {
            return this
        }
    }

    data class Pointer(val target: Type) : Type {
        override val tname = "${target.tname}*"
        override val size = 1

        override fun isAssignableTo(other: Type): Boolean {
            return when (other) {
                is Pointer -> target.isAssignableTo(other.target)
                else -> false
            }
        }

        override fun fillGeneric(generic: Type, type: Type): Type {
            return Pointer(target.fillGeneric(generic, type))
        }
    }

    data class Array(val type: Type, val length: Int) : Type {
        override val tname = "${type.tname}[$length]"
        override val size = type.size * length

        override fun isAssignableTo(other: Type): Boolean {
            return when (other) {
                is Array -> type.isAssignableTo(other.type)
                else -> false
            }
        }

        override fun fillGeneric(generic: Type, type: Type): Type {
            return Array(type.fillGeneric(generic, type), length)
        }
    }

    data class Structure(override val tname: String, val fields: List<Pair<String, Type>>) : Type {
        override val size = fields.sumOf { it.second.size }

        override fun fillGeneric(generic: Type, type: Type): Type {
            return Structure(tname, fields.map { (name, type) -> name to type.fillGeneric(generic, type) })
        }
    }

    data class Generic(override val tname: String) : Type {
        override val size: Int
            get() = throw UnsupportedOperationException("Generic type has no size")

        override fun fillGeneric(generic: Type, type: Type): Type {
            return if (this == generic) type else this
        }
    }

    fun isAssignableTo(other: Type): Boolean {
        return when (other) {
            this, Any -> true
            else -> false
        }
    }

    fun fillGeneric(generic: Type, type: Type): Type

    companion object {
        fun parse(tname: String, declaredTypes: Set<Type>): Type? {
            return when {
                tname.endsWith('*') -> Pointer(parse(tname.dropLast(1), declaredTypes) ?: return null)
                tname.endsWith(']') -> {
                    val type = tname.substringBeforeLast('[', "")
                    if (type.isEmpty()) return null
                    val length = tname.substringAfterLast('[').dropLast(1)
                    Array(parse(type, declaredTypes) ?: return null, length.toInt())
                }

                tname == "Number" -> Primitive.NUMBER
                tname == "Boolean" -> Primitive.BOOLEAN
                declaredTypes.any { it.tname == tname } -> declaredTypes.first { it.tname == tname }
                else -> null
            }
        }

        val STRING = Structure("String", listOf("length" to Primitive.NUMBER, "data" to Pointer(Primitive.CHAR)))
    }
}
