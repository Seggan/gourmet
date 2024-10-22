package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import org.antlr.v4.runtime.Token

enum class AssignType(private val token: Int, val op: BinOp?) {
    ASSIGN(GourmetParser.ASSIGN, null),
    PLUS_ASSIGN(GourmetParser.PLUS_ASSIGN, BinOp.PLUS),
    MINUS_ASSIGN(GourmetParser.MINUS_ASSIGN, BinOp.MINUS),
    TIMES_ASSIGN(GourmetParser.MULT_ASSIGN, BinOp.TIMES),
    DIV_ASSIGN(GourmetParser.DIV_ASSIGN, BinOp.DIV),
    MOD_ASSIGN(GourmetParser.MOD_ASSIGN, BinOp.MOD);

    companion object {
        fun fromToken(token: Token) = entries.first { it.token == token.type }
    }
}