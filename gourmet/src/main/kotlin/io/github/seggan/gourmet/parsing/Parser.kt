package io.github.seggan.gourmet.parsing

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import java.awt.SystemColor.text

@Suppress("MemberVisibilityCanBePrivate")
object Parser : Grammar<List<AstNode>>() {

    val POPEN by literalToken("(")
    val PCLOSE by literalToken(")")
    val BOPEN by literalToken("[")
    val BCLOSE by literalToken("]")

    val ID by regexToken("[a-zA-Z_][a-zA-Z0-9_]*")
    val NUMBER by regexToken("""[+-]?\d+(\.\d+)?""")
    val STRING by regexToken(""""([^"\\]|\\.)*"""")
    val TRUE by literalToken("true")
    val FALSE by literalToken("false")

    @Suppress("unused")
    val WS by regexToken("\\s+", ignore = true)

    @Suppress("unused")
    val COMMENT by regexToken(";.*\\n", ignore = true)

    val number by NUMBER use { AstNode.Number(text.toBigDecimal()) }
    val string by STRING use { AstNode.String(unescapeString(text.drop(1).dropLast(1))) }
    val boolean by (TRUE or FALSE) use { AstNode.Boolean(text.toBoolean()) }
    val vector by -BOPEN * zeroOrMore(parser(this::expr)) * -BCLOSE map { AstNode.Vector(it) }
    val symbol by ID use { AstNode.Symbol(text) }
    val application by -POPEN * ID * zeroOrMore(parser(this::expr)) * -PCLOSE map { (fn, args) ->
        AstNode.Application(fn.text, args)
    }

    val expr: Parser<AstNode> by number or string or boolean or vector or symbol or application

    override val rootParser by zeroOrMore(expr)
}

private fun unescapeString(s: String): String {
    val sb = StringBuilder()
    var i = 1
    while (i < s.length - 1) {
        val c = s[i]
        if (c == '\\') {
            i++
            when (val escaped = s[i]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                '\\' -> sb.append('\\')
                '"' -> sb.append('"')
                else -> sb.append(escaped)
            }
        } else {
            sb.append(c)
        }
        i++
    }
    return sb.toString()
}