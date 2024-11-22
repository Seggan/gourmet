package io.github.seggan.gourmet.parsing

import java.math.BigDecimal
import java.sql.Struct

sealed interface AstNode<T> {

    val extra: T

    data class File<T>(
        val functions: List<Function<T>>,
        val structs: List<Struct<T>>,
        override val extra: T
    ) : AstNode<T> {
        operator fun plus(other: File<T>): File<T> = File(
            functions = functions + other.functions,
            structs = structs + other.structs,
            extra = extra
        )
    }

    data class Struct<T>(
        val name: String,
        val generics: List<String>,
        val fields: List<Pair<String, TypeName>>,
        override val extra: T
    ) : AstNode<T>

    data class Function<T>(
        val attributes: Set<String>,
        val name: String,
        val genericArgs: List<String>,
        val args: List<Pair<String, TypeName>>,
        val returnType: TypeName?,
        val body: Block<T>,
        override val extra: T
    ) : AstNode<T>

    sealed interface Statement<T> : AstNode<T>

    data class Block<T>(
        val statements: List<Statement<T>>,
        override val extra: T
    ) : Statement<T>

    data class Declaration<T>(
        val name: String,
        val type: TypeName?,
        val value: Expression<T>?,
        override val extra: T
    ) : Statement<T>

    data class Assignment<T>(
        val isPointer: Boolean,
        val name: String,
        val target: String?,
        val assignType: AssignType,
        val value: Expression<T>,
        override val extra: T
    ) : Statement<T>

    data class Return<T>(
        val value: Expression<T>?,
        override val extra: T
    ) : Statement<T>

    data class If<T>(
        val condition: Expression<T>,
        val thenBody: Statement<T>,
        val elseBody: Statement<T>?,
        override val extra: T
    ) : Statement<T>

    data class For<T>(
        val init: Statement<T>?,
        val condition: Expression<T>,
        val update: Statement<T>?,
        val body: Statement<T>,
        override val extra: T
    ) : Statement<T>

    data class While<T>(
        val condition: Expression<T>,
        val body: Statement<T>,
        override val extra: T
    ) : Statement<T>

    data class DoWhile<T>(
        val body: Statement<T>,
        val condition: Expression<T>,
        override val extra: T
    ) : Statement<T>

    data class Break<T>(override val extra: T) : Statement<T>

    data class Continue<T>(override val extra: T) : Statement<T>

    sealed interface Expression<T> : Statement<T>

    data class NumberLiteral<T>(
        val value: BigDecimal,
        override val extra: T
    ) : Expression<T>

    data class StringLiteral<T>(
        val value: String,
        override val extra: T
    ) : Expression<T>

    data class CharLiteral<T>(
        val value: Char,
        override val extra: T
    ) : Expression<T>

    data class BooleanLiteral<T>(
        val value: Boolean,
        override val extra: T
    ) : Expression<T>

    data class Variable<T>(
        val name: String,
        override val extra: T
    ) : Expression<T>

    data class FunctionCall<T>(
        val name: String,
        val genericArgs: List<TypeName>,
        val args: List<Expression<T>>,
        override val extra: T
    ) : Expression<T>

    data class BinaryExpression<T>(
        val left: Expression<T>,
        val operator: BinOp,
        val right: Expression<T>,
        override val extra: T
    ) : Expression<T>

    data class UnaryExpression<T>(
        val operator: UnOp,
        val value: Expression<T>,
        override val extra: T
    ) : Expression<T>

    data class MemberAccess<T>(
        val target: Expression<T>,
        val member: String,
        override val extra: T
    ) : Expression<T>

    data class StructInstance<T>(
        val type: TypeName,
        val values: List<Pair<String, Expression<T>>>,
        override val extra: T
    ) : Expression<T>
}

fun AstNode<*>.stringify(): String = stringify(this)

private fun stringify(value: Any?): String {
    if (value is List<*>) return value.joinToString(prefix = "[", postfix = "]", separator = ", ", transform = ::stringify)
    if (value !is AstNode<*>) return value.toString()

    val clazz = value::class.java
    val name = clazz.simpleName
    val fields = clazz.declaredFields.filter { it.name != "location" }
    val sb = StringBuilder("\n")
    sb.appendLine(name)
    for (field in fields) {
        field.isAccessible = true
        val fieldName = field.name
        val fieldValue = field.get(value)
        sb.append("$fieldName: ")
        sb.appendLine(stringify(fieldValue))
    }
    return sb.toString().lines().joinToString("\n") { "  $it" }
}