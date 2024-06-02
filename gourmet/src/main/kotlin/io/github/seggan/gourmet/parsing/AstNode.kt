@file:OptIn(ExperimentalContracts::class)

package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.compilation.CompilationException
import java.math.BigDecimal
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface AstNode {
    data class Number(val value: BigDecimal) : AstNode
    data class String(val value: kotlin.String) : AstNode
    data class Boolean(val value: kotlin.Boolean) : AstNode
    data class Vector(val value: List<AstNode>) : AstNode
    data class Symbol(val value: kotlin.String) : AstNode
    data class Application(val fn: kotlin.String, val args: List<AstNode>) : AstNode
}

inline fun <reified T : AstNode> AstNode.convertTo(): T {
    contract {
        returns() implies (this@convertTo is T)
    }
    if (this is T) {
        return this
    } else {
        throw CompilationException("Expected ${T::class.simpleName}, got $this")
    }
}

fun AstNode.numberValue(): BigDecimal {
    contract {
        returns() implies (this@numberValue is AstNode.Number)
    }
    return convertTo<AstNode.Number>().value
}

fun AstNode.stringValue(): String {
    contract {
        returns() implies (this@stringValue is AstNode.String)
    }
    return convertTo<AstNode.String>().value
}

fun AstNode.booleanValue(): Boolean {
    contract {
        returns() implies (this@booleanValue is AstNode.Boolean)
    }
    return convertTo<AstNode.Boolean>().value
}

fun AstNode.vectorValue(): List<AstNode> {
    contract {
        returns() implies (this@vectorValue is AstNode.Vector)
    }
    return convertTo<AstNode.Vector>().value
}

fun AstNode.symbolValue(): String {
    contract {
        returns() implies (this@symbolValue is AstNode.Symbol)
    }
    return convertTo<AstNode.Symbol>().value
}