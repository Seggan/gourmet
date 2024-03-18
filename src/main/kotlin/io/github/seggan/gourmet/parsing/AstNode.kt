package io.github.seggan.gourmet.parsing

sealed interface AstNode {

    sealed interface Expression : AstNode

    data class Number(val value: UInt) : Expression
    data class Register(val name: String) : Expression
    data class Stack(val name: String) : Expression
    data class Variable(val name: String) : Expression
    data class Block(val body: List<AstNode>) : Expression

    data class Invocation(val stack: Stack?, val name: String, val args: List<Expression>) : AstNode
    data class Macro(val name: String, val args: List<String>, val body: List<AstNode>) : AstNode
    data class Function(val name: String, val args: List<String>, val body: List<AstNode>) : AstNode
}