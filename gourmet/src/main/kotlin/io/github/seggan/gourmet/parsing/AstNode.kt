package io.github.seggan.gourmet.parsing

import java.math.BigDecimal

sealed interface AstNode {
    data class Number(val value: BigDecimal) : AstNode
    data class String(val value: kotlin.String) : AstNode
    data class Boolean(val value: kotlin.Boolean) : AstNode
    data class Vector(val value: List<AstNode>) : AstNode
    data class Symbol(val value: kotlin.String) : AstNode
    data class Application(val fn: kotlin.String, val args: List<AstNode>) : AstNode
}