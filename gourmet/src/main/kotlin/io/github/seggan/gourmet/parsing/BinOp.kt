package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.compilation.ir.Argument
import io.github.seggan.gourmet.compilation.ir.Insn
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.typing.TypeException
import io.github.seggan.gourmet.util.Location
import org.antlr.v4.runtime.Token

enum class BinOp(private val token: Int) {

    PLUS(GourmetParser.PLUS) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot add $left and $right", location)
        }

        override fun compile() = listOf(Insn("add", Argument.UseStack))
    },
    MINUS(GourmetParser.MINUS) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot subtract $left and $right", location)
        }

        override fun compile() = listOf(Insn("sub", Argument.UseStack))
    },
    TIMES(GourmetParser.STAR) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot multiply $left and $right", location)
        }

        override fun compile() = listOf(Insn("mul", Argument.UseStack))
    },
    DIV(GourmetParser.SLASH) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot divide $left and $right", location)
        }

        override fun compile() = listOf(Insn("div", Argument.UseStack))
    },
    MOD(GourmetParser.PERCENT) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot modulo $left and $right", location)
        }

        override fun compile() = TODO()
    },

    EQ(GourmetParser.EQ) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(right)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }

        override fun compile() = listOf(Insn("eq", Argument.UseStack))
    },
    NEQ(GourmetParser.NE) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(right)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }

        override fun compile() = listOf(
            Insn("eq", Argument.UseStack),
            Insn("not", Argument.UseStack)
        )
    },
    LT(GourmetParser.LT) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }

        override fun compile() = listOf(Insn("lt", Argument.UseStack))
    },
    GT(GourmetParser.GT) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }

        override fun compile() = listOf(Insn("gt", Argument.UseStack))
    },
    LTE(GourmetParser.LE) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }

        override fun compile() = listOf(
            Insn("gt", Argument.UseStack),
            Insn("not", Argument.UseStack)
        )
    },
    GTE(GourmetParser.GE) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.NUMBER) && right.isAssignableTo(Type.Primitive.NUMBER)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }

        override fun compile() = listOf(
            Insn("lt", Argument.UseStack),
            Insn("not", Argument.UseStack)
        )
    },

    AND(GourmetParser.AND) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.BOOLEAN) && right.isAssignableTo(Type.Primitive.BOOLEAN)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot apply AND to $left and $right", location)
        }

        override fun compile() = listOf(Insn("and", Argument.UseStack))
    },
    OR(GourmetParser.OR) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left.isAssignableTo(Type.Primitive.BOOLEAN) && right.isAssignableTo(Type.Primitive.BOOLEAN)) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot apply OR to $left and $right", location)
        }

        override fun compile() = listOf(Insn("or", Argument.UseStack))
    };

    abstract fun checkType(left: Type, right: Type, location: Location): Type
    abstract fun compile(): List<Insn>

    companion object {
        fun fromToken(token: Token): BinOp = entries.first { it.token == token.type }
    }
}