package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.compilation.ir.Argument
import io.github.seggan.gourmet.compilation.ir.Insn
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

        override fun compile() = listOf(Insn("not", Argument.UseStack))
    },
    NEG(GourmetParser.MINUS) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg != Type.Primitive.NUMBER) {
                throw TypeException("Expected Number, got $arg", location)
            }
            return Type.Primitive.NUMBER
        }

        override fun compile() = listOf(Insn("neg", Argument.UseStack))
    },
    DEREF(GourmetParser.STAR) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg !is Type.Pointer) {
                throw TypeException("Expected a pointer, got $arg", location)
            }
            return arg.target
        }

        override fun compile() = TODO()
    },
    ASM(GourmetParser.ASM) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg != Type.STRING) {
                throw TypeException("Expected String, got $arg", location)
            }
            return Type.Never
        }

        override fun compile() = throw AssertionError("asm is compiled separately")
    },
    SIZEOF(GourmetParser.SIZEOF) {
        override fun checkType(arg: Type, location: Location): Type {
            return Type.Primitive.NUMBER
        }

        override fun compile() = throw AssertionError("sizeof is compiled separately")
    };

    abstract fun checkType(arg: Type, location: Location): Type
    abstract fun compile(): List<Insn>

    companion object {
        fun fromToken(token: Token): UnOp {
            return entries.first { it.token == token.type }
        }
    }
}