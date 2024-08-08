package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import org.antlr.v4.runtime.Token

enum class BinOp(private val token: Int) {

    PLUS(GourmetParser.PLUS),
    MINUS(GourmetParser.MINUS),
    TIMES(GourmetParser.STAR),
    DIV(GourmetParser.SLASH),
    MOD(GourmetParser.PERCENT),

    EQ(GourmetParser.EQ),
    NEQ(GourmetParser.NE),
    LT(GourmetParser.LT),
    GT(GourmetParser.GT),
    LTE(GourmetParser.LE),
    GTE(GourmetParser.GE),

    AND(GourmetParser.AND),
    OR(GourmetParser.OR);

    companion object {
        fun fromToken(token: Token): BinOp {
            return entries.first { it.token == token.type }
        }
    }
}