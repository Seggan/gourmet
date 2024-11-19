package io.github.seggan.recipe.parsing

import com.github.h0tk3y.betterParse.parser.parseToEnd
import java.math.BigDecimal

sealed interface AstNode {

    sealed interface Expression : AstNode

    data class Number(val value: BigDecimal) : Expression
    data class Register(val name: String) : Expression
    data class Stack(val name: String) : Expression
    data class Variable(val name: String) : Expression
    data class Block(val body: List<Invocation>) : Expression {
        companion object {
            val NOP_BLOCK = Parser.block.parseToEnd(Parser.tokenizer.tokenize("{ exec {}; }"))
        }
    }

    data class Invocation(val stack: Stack?, val name: String, val args: List<Expression>) : AstNode
    data class Macro(val name: String, val args: List<String>, val body: List<Invocation>) : AstNode
    data class Function(val name: String, val args: List<String>, val body: List<Invocation>) : AstNode
}

fun AstNode.flatten(): List<AstNode> = when (this) {
    is AstNode.Block -> body.flatMap(AstNode::flatten)
    is AstNode.Expression -> listOf(this)
    is AstNode.Invocation -> listOf(this) + args.flatMap(AstNode::flatten)
    is AstNode.Macro -> listOf(this) + body.flatMap(AstNode::flatten)
    is AstNode.Function -> listOf(this) + body.flatMap(AstNode::flatten)
}