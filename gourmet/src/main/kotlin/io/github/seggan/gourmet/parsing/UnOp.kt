package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.compilation.Blocks
import io.github.seggan.gourmet.compilation.IrGenerator
import io.github.seggan.gourmet.compilation.ir.InsnMacros
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.typing.TypeException
import io.github.seggan.gourmet.util.Location
import org.antlr.v4.runtime.Token

enum class UnOp(private val token: Int) {

    NOT(GourmetParser.NOT) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg.isAssignableTo(Type.Primitive.BOOLEAN)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Expected Boolean, got $arg", location)
        }

        override fun IrGenerator.compile(type: Type): Blocks = buildBlock {
            +InsnMacros.useStack { InsnMacros.bool(it, not = true) }
        }
    },
    NEG(GourmetParser.MINUS) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Expected Number, got $arg", location)
        }

        override fun IrGenerator.compile(type: Type): Blocks = buildBlock {
            +InsnMacros.useStack(InsnMacros::neg)
        }
    },
    DEREF(GourmetParser.STAR) {
        override fun checkType(arg: Type, location: Location): Type {
            if (arg is Type.Pointer) {
                return arg.target
            }
            throw TypeException("Expected a pointer, got $arg", location)
        }

        override fun IrGenerator.compile(type: Type): Blocks = getPointer(type as Type.Pointer)
    };

    abstract fun checkType(arg: Type, location: Location): Type
    abstract fun IrGenerator.compile(type: Type): Blocks

    companion object {
        fun fromToken(token: Token): UnOp {
            return entries.first { it.token == token.type }
        }
    }
}