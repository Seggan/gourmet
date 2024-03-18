package io.github.seggan.gourmet.parsing

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import io.github.seggan.gourmet.parsing.Parser.getValue
import io.github.seggan.gourmet.parsing.Parser.provideDelegate

object Parser : Grammar<List<AstNode>>() {

    val POPEN by literalToken("(")
    val PCLOSE by literalToken(")")
    val BOPEN by literalToken("{")
    val BCLOSE by literalToken("}")
    val COMMA by literalToken(",")
    val AT by literalToken("@")
    val DOLLAR by literalToken("$")
    val PERIOD by literalToken(".")
    val SEMICOLON by literalToken(";")

    val MACRO by literalToken("macro")
    val FUN by literalToken("fun")

    val ID by regexToken("[a-zA-Z_][a-zA-Z0-9_]*")
    val NUMBER by regexToken("\\d+")

    @Suppress("unused")
    val WS by regexToken("\\s+", ignore = true)
    @Suppress("unused")
    val COMMENT by regexToken("//.*\\n", ignore = true)

    val number by NUMBER use { AstNode.Number(text.toUInt()) }
    val register by -DOLLAR * ID use { AstNode.Register(text) }
    val stack by -AT * ID use { AstNode.Stack(text) }
    val variable by ID use { AstNode.Variable(text) }
    val block by -BOPEN * zeroOrMore(parser(::invocation)) * -BCLOSE map { AstNode.Block(it) }

    val expr: Parser<AstNode.Expression> by number or register or stack or variable or block

    val invocation by optional(stack * -PERIOD) *
            ID * separatedTerms(expr, COMMA, acceptZero = true) *
            -SEMICOLON map { (stack, name, args) -> AstNode.Invocation(stack, name.text, args) }

    val macro by -MACRO * ID *
            -POPEN * separatedTerms(ID, COMMA, acceptZero = true) * -PCLOSE *
            block map { (name, args, body) -> AstNode.Macro(name.text, args.map { it.text }, body.body) }

    val function by -FUN * ID *
            -POPEN * separatedTerms(ID, COMMA, acceptZero = true) * -PCLOSE *
            block map { (name, args, body) -> AstNode.Function(name.text, args.map { it.text }, body.body) }

    override val rootParser by zeroOrMore(macro or function or invocation)
}