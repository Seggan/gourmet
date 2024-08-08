package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import org.antlr.v4.runtime.Token

enum class UnOp(private val token: Int) {

    NOT(GourmetParser.NOT),
    NEG(GourmetParser.MINUS),
    DEREF(GourmetParser.STAR);

    companion object {
        fun fromToken(token: Token): UnOp {
            return entries.first { it.token == token.type }
        }
    }
}