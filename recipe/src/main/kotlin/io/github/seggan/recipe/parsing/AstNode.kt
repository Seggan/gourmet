package io.github.seggan.recipe.parsing

import java.math.BigDecimal

sealed interface AstNode {

    sealed interface Expression : AstNode

    data class Number(val value: BigDecimal) : Expression
    data class Register(val name: String) : Expression
    data class Stack(val name: String) : Expression
    data class Variable(val name: String) : Expression
    data class Block(val body: List<Invocation>) : Expression

    data class Invocation(val stack: Stack?, val name: String, val args: List<Expression>) : AstNode
    data class Macro(val name: String, val args: List<String>, val body: List<Invocation>) : AstNode
    data class Function(val name: String, val args: List<String>, val body: List<Invocation>) : AstNode
}