package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.util.Location
import java.math.BigDecimal

sealed interface AstNode<T> {

    val extra: T
    val location: Location

    data class File<T>(
        val functions: List<Function<T>>,
        override val location: Location,
        override val extra: T
    ) : AstNode<T>

    data class Function<T>(
        val name: String,
        val args: List<Pair<String, TypeName>>,
        val returnType: TypeName?,
        val block: Block<T>,
        override val location: Location,
        override val extra: T
    ) : AstNode<T>

    sealed interface Statement<T> : AstNode<T>

    data class Statements<T>(
        val statements: List<Statement<T>>,
        override val location: Location,
        override val extra: T
    ) : Statement<T>

    data class Block<T>(
        val statements: Statements<T>,
        override val location: Location,
        override val extra: T
    ) : Statement<T>

    data class Declaration<T>(
        val name: String,
        val type: TypeName?,
        val value: Expression<T>?,
        override val location: Location,
        override val extra: T
    ) : Statement<T>

    data class Assignment<T>(
        val name: String,
        val value: Expression<T>,
        override val location: Location,
        override val extra: T
    ) : Statement<T>

    data class Return<T>(
        val value: Expression<T>,
        override val location: Location,
        override val extra: T
    ) : Statement<T>

    data class If<T>(
        val condition: Expression<T>,
        val thenBlock: Statement<T>,
        val elseBlock: Statement<T>?,
        override val location: Location,
        override val extra: T
    ) : Statement<T>

    data class While<T>(
        val condition: Expression<T>,
        val block: Statement<T>,
        override val location: Location,
        override val extra: T
    ) : Statement<T>

    data class DoWhile<T>(
        val block: Statement<T>,
        val condition: Expression<T>,
        override val location: Location,
        override val extra: T
    ) : Statement<T>

    sealed interface Expression<T> : Statement<T>

    data class NumberLiteral<T>(
        val value: BigDecimal,
        override val location: Location,
        override val extra: T
    ) : Expression<T>

    data class StringLiteral<T>(
        val value: String,
        override val location: Location,
        override val extra: T
    ) : Expression<T>

    data class CharLiteral<T>(
        val value: Char,
        override val location: Location,
        override val extra: T
    ) : Expression<T>

    data class BooleanLiteral<T>(
        val value: Boolean,
        override val location: Location,
        override val extra: T
    ) : Expression<T>

    data class Variable<T>(
        val name: String,
        override val location: Location,
        override val extra: T
    ) : Expression<T>

    data class FunctionCall<T>(
        val name: String,
        val genericArgs: List<TypeName>,
        val args: List<Expression<T>>,
        override val location: Location,
        override val extra: T
    ) : Expression<T>

    data class BinaryExpression<T>(
        val left: Expression<T>,
        val operator: BinOp,
        val right: Expression<T>,
        override val location: Location,
        override val extra: T
    ) : Expression<T>

    data class UnaryExpression<T>(
        val operator: UnOp,
        val value: Expression<T>,
        override val location: Location,
        override val extra: T
    ) : Expression<T>

    data class MemberAccess<T>(
        val target: Expression<T>,
        val member: String,
        override val location: Location,
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