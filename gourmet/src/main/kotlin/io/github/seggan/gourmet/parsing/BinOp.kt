package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.typing.Type
import io.github.seggan.gourmet.typing.TypeException
import io.github.seggan.gourmet.util.Location
import org.antlr.v4.runtime.Token

enum class BinOp(private val token: Int) {

    PLUS(GourmetParser.PLUS) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot add $left and $right", location)
        }
    },
    MINUS(GourmetParser.MINUS) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot subtract $left and $right", location)
        }
    },
    TIMES(GourmetParser.STAR) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot multiply $left and $right", location)
        }
    },
    DIV(GourmetParser.SLASH) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot divide $left and $right", location)
        }
    },
    MOD(GourmetParser.PERCENT) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.NUMBER
            }
            throw TypeException("Cannot modulo $left and $right", location)
        }
    },

    EQ(GourmetParser.EQ) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == right) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }
    },
    NEQ(GourmetParser.NE) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == right) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }
    },
    LT(GourmetParser.LT) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }
    },
    GT(GourmetParser.GT) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }
    },
    LTE(GourmetParser.LE) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }
    },
    GTE(GourmetParser.GE) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.NUMBER && right == Type.Primitive.NUMBER) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot compare $left and $right", location)
        }
    },

    AND(GourmetParser.AND) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.BOOLEAN && right == Type.Primitive.BOOLEAN) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot apply AND to $left and $right", location)
        }
    },
    OR(GourmetParser.OR) {
        override fun checkType(left: Type, right: Type, location: Location): Type {
            if (left == Type.Primitive.BOOLEAN && right == Type.Primitive.BOOLEAN) {
                return Type.Primitive.BOOLEAN
            }
            throw TypeException("Cannot apply OR to $left and $right", location)
        }
    };

    abstract fun checkType(left: Type, right: Type, location: Location): Type

    companion object {
        fun fromToken(token: Token): BinOp {
            return entries.first { it.token == token.type }
        }
    }
}