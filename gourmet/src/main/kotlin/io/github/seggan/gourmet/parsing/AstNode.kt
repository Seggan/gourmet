@file:OptIn(ExperimentalContracts::class)

package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.compilation.CompilationException
import io.github.seggan.gourmet.compilation.Type
import java.math.BigDecimal
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface AstNode<T> {

    val extra: T

    data class Number<T>(val value: BigDecimal, override val extra: T) : AstNode<T>
    data class String<T>(val value: kotlin.String, override val extra: T) : AstNode<T>
    data class Boolean<T>(val value: kotlin.Boolean, override val extra: T) : AstNode<T>
    data class Array<T>(val value: List<AstNode<T>>, override val extra: T) : AstNode<T>
    data class Symbol<T>(val value: kotlin.String, override val extra: T) : AstNode<T>
    data class Application<T>(val fn: kotlin.String, val args: List<AstNode<T>>, override val extra: T) : AstNode<T>
}

inline fun <T, reified N : AstNode<T>> AstNode<T>.convertTo(): N {
    contract {
        returns() implies (this@convertTo is N)
    }
    if (this is N) {
        return this
    } else {
        throw CompilationException("Expected ${N::class.simpleName}, got $this")
    }
}

fun <T> AstNode<T>.numberValue(): BigDecimal {
    return convertTo<T, AstNode.Number<T>>().value
}

fun <T> AstNode<T>.stringValue(): kotlin.String {
    return convertTo<T, AstNode.String<T>>().value
}

fun <T> AstNode<T>.booleanValue(): kotlin.Boolean {
    return convertTo<T, AstNode.Boolean<T>>().value
}

fun <T> AstNode<T>.arrayValue(): List<AstNode<T>> {
    return convertTo<T, AstNode.Array<T>>().value
}

fun <T> AstNode<T>.symbolValue(): kotlin.String {
    return convertTo<T, AstNode.Symbol<T>>().value
}
