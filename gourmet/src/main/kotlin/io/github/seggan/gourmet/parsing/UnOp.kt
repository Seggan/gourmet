package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.typing.TypeException
import io.github.seggan.gourmet.util.Location
import org.antlr.v4.runtime.Token

enum class UnOp(private val token: Int) {

    NOT(GourmetParser.NOT) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg != Type.Primitive.BOOLEAN) {
                throw TypeException("Expected Boolean, got $arg", location)
            }
            return Type.Primitive.BOOLEAN
        }
    },
    NEG(GourmetParser.MINUS) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg != Type.Primitive.NUMBER) {
                throw TypeException("Expected Number, got $arg", location)
            }
            return Type.Primitive.NUMBER
        }
    },
    DEREF(GourmetParser.STAR) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg !is Type.Pointer) {
                throw TypeException("Expected Pointer, got $arg", location)
            }
            return arg.target
        }
    };

    abstract fun checkType(arg: Type, location: Location): Type

    companion object {
        fun fromToken(token: Token): UnOp {
            return entries.first { it.token == token.type }
        }
    }
}