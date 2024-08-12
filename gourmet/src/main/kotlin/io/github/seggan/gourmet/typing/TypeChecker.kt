package io.github.seggan.gourmet.typing

import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.parsing.TypeName
import io.github.seggan.gourmet.util.Location

class TypeChecker(private val ast: AstNode.File<Unit>) {

    private val types = mutableMapOf<TypeName, Type>(
        TypeName.Simple("Number") to Type.Primitive.NUMBER,
        TypeName.Simple("String") to Type.STRING,
        TypeName.Simple("Boolean") to Type.Primitive.BOOLEAN,
        TypeName.Simple("Char") to Type.Primitive.CHAR,
        TypeName.Simple("Unit") to Type.Unit,
        TypeName.Simple("Never") to Type.Never,
    )

    private val functions = mutableListOf<Pair<String, Type.Function>>()
    private val scopes = ArrayDeque<MutableList<Pair<String, Type>>>()

    private fun TypeName.resolve(location: Location): Type {
        return when (this) {
            is TypeName.Simple -> types[this] ?: throw TypeException("Unknown type: $this", location)
            is TypeName.Pointer -> Type.Pointer(type.resolve(location))
            is TypeName.Generic -> Type.Generic(name)
        }
    }

    private fun findVariable(name: String, location: Location): Type {
        for (scope in scopes) {
            for ((n, t) in scope) {
                if (n == name) return t
            }
        }
        throw TypeException("Unknown variable: $name", location)
    }

    fun check(): AstNode.File<Type> {
        return AstNode.File(ast.functions.map(::checkFunction), ast.location, Type.Unit)
    }

    private fun checkFunction(node: AstNode.Function<Unit>): AstNode.Function<Type> {
        val args = node.args.map { (name, type) -> name to type.resolve(node.location) }
        scopes.addFirst(args.toMutableList())
        val returnType = node.returnType?.resolve(node.location) ?: Type.Unit
        val signature = Type.Function(emptyList(), args.map { it.second }, returnType)
        functions.add(node.name to signature)
        val block = checkBlock(node.body)
        scopes.removeFirst()
        return AstNode.Function(node.name, node.args, node.returnType, block, node.location, signature)
    }

    private fun checkBlock(node: AstNode.Block<Unit>): AstNode.Block<Type> {
        scopes.addFirst(mutableListOf())
        return AstNode.Block(checkStatements(node.statements), node.location, Type.Unit)
            .also { scopes.removeFirst() }
    }

    private fun checkStatements(node: AstNode.Statements<Unit>): AstNode.Statements<Type> {
        return AstNode.Statements(node.statements.map(::checkStatement), node.location, Type.Unit)
    }

    private fun checkStatement(node: AstNode.Statement<Unit>): AstNode.Statement<Type> {
        return when (node) {
            is AstNode.Declaration -> checkDeclaration(node)
            is AstNode.Assignment -> checkAssignment(node)
            is AstNode.Return -> checkReturn(node)
            is AstNode.Block -> checkBlock(node)
            is AstNode.If -> checkIf(node)
            is AstNode.Statements -> checkStatements(node)
            is AstNode.DoWhile -> checkDoWhile(node)
            is AstNode.While -> checkWhile(node)
            is AstNode.Expression -> checkExpression(node)
        }
    }

    private fun checkDeclaration(node: AstNode.Declaration<Unit>): AstNode.Declaration<Type> {
        val value = node.value?.let(::checkExpression)
        val type = node.type?.resolve(node.location)
            ?: value?.extra
            ?: throw TypeException("Cannot infer type of declaration", node.location)
        if (value != null && !value.extra.isAssignableTo(type)) {
            throw TypeException("Cannot assign ${value.extra} to $type", node.location)
        }
        scopes.first().add(node.name to type)
        return AstNode.Declaration(node.name, node.type, value, node.location, type)
    }

    private fun checkAssignment(node: AstNode.Assignment<Unit>): AstNode.Assignment<Type> {
        val value = checkExpression(node.value)
        val type = findVariable(node.name, node.location)
        if (!value.extra.isAssignableTo(type)) {
            throw TypeException("Cannot assign ${value.extra} to $type", node.location)
        }
        return AstNode.Assignment(node.name, value, node.location, type)
    }

    private fun checkReturn(node: AstNode.Return<Unit>): AstNode.Return<Type> {
        val value = node.value?.let(::checkExpression)
        val valueType = value?.extra ?: Type.Unit
        val returnType = functions.last().second.returnType
        if (!valueType.isAssignableTo(returnType)) {
            throw TypeException(
                "Cannot return $valueType from function with return type $returnType",
                node.location
            )
        }
        return AstNode.Return(value, node.location, returnType)
    }

    private fun checkIf(node: AstNode.If<Unit>): AstNode.If<Type> {
        val condition = checkExpression(node.condition)
        if (condition.extra != Type.Primitive.BOOLEAN) {
            throw TypeException("Condition must be a boolean", condition.location)
        }
        val thenBlock = checkStatement(node.thenBody)
        val elseBlock = node.elseBody?.let(::checkStatement)
        return AstNode.If(condition, thenBlock, elseBlock, node.location, Type.Unit)
    }

    private fun checkWhile(node: AstNode.While<Unit>): AstNode.While<Type> {
        val condition = checkExpression(node.condition)
        if (condition.extra != Type.Primitive.BOOLEAN) {
            throw TypeException("Condition must be a boolean", condition.location)
        }
        val block = checkStatement(node.body)
        return AstNode.While(condition, block, node.location, Type.Unit)
    }

    private fun checkDoWhile(node: AstNode.DoWhile<Unit>): AstNode.DoWhile<Type> {
        val block = checkStatement(node.body)
        val condition = checkExpression(node.condition)
        if (condition.extra != Type.Primitive.BOOLEAN) {
            throw TypeException("Condition must be a boolean", condition.location)
        }
        return AstNode.DoWhile(block, condition, node.location, Type.Unit)
    }

    private fun checkExpression(node: AstNode.Expression<Unit>): AstNode.Expression<Type> {
        return when (node) {
            is AstNode.BooleanLiteral -> AstNode.BooleanLiteral(node.value, node.location, Type.Primitive.BOOLEAN)
            is AstNode.CharLiteral -> AstNode.CharLiteral(node.value, node.location, Type.Primitive.CHAR)
            is AstNode.NumberLiteral -> AstNode.NumberLiteral(node.value, node.location, Type.Primitive.NUMBER)
            is AstNode.StringLiteral -> AstNode.StringLiteral(node.value, node.location, Type.STRING)
            is AstNode.Variable -> AstNode.Variable(node.name, node.location, findVariable(node.name, node.location))
            is AstNode.BinaryExpression -> checkBinaryExpression(node)
            is AstNode.UnaryExpression -> checkUnaryExpression(node)
            is AstNode.FunctionCall -> checkFunctionCall(node)
            is AstNode.MemberAccess -> checkMemberAccess(node)
        }
    }

    private fun checkBinaryExpression(node: AstNode.BinaryExpression<Unit>): AstNode.BinaryExpression<Type> {
        val left = checkExpression(node.left)
        val right = checkExpression(node.right)
        val type = node.operator.checkType(left.extra, right.extra, node.location)
        return AstNode.BinaryExpression(left, node.operator, right, node.location, type)
    }

    private fun checkUnaryExpression(node: AstNode.UnaryExpression<Unit>): AstNode.UnaryExpression<Type> {
        val value = checkExpression(node.value)
        val type = node.operator.checkType(value.extra, node.location)
        return AstNode.UnaryExpression(node.operator, value, node.location, type)
    }

    private fun checkFunctionCall(node: AstNode.FunctionCall<Unit>): AstNode.FunctionCall<Type> {
        var exception = TypeException("Unknown function: ${node.name}", node.location)
        outer@ for (function in functions) {
            if (function.first != node.name) continue
            var type = function.second
            if (node.genericArgs.size != type.genericArgs.size) {
                exception = TypeException(
                    "Expected ${type.genericArgs.size} generic arguments, got ${node.genericArgs.size}",
                    node.location
                )
                continue
            }
            if (node.args.size != type.args.size) {
                exception = TypeException("Expected ${type.args.size} arguments, got ${node.args.size}", node.location)
                continue
            }
            for ((generic, provided) in type.genericArgs.zip(node.genericArgs)) {
                type = type.fillGeneric(generic, provided.resolve(node.location)) as Type.Function
            }
            if (type.genericArgs.isNotEmpty()) {
                exception = TypeException("Generic arguments not fully resolved", node.location)
                continue
            }
            val args = node.args.map(::checkExpression)
            for ((arg, provided) in type.args.zip(args)) {
                if (!provided.extra.isAssignableTo(arg)) {
                    exception = TypeException("Cannot assign ${provided.extra} to $arg", provided.location)
                    continue@outer
                }
            }
            return AstNode.FunctionCall(node.name, node.genericArgs, args, node.location, type.returnType)
        }
        throw exception
    }

    private fun checkMemberAccess(node: AstNode.MemberAccess<Unit>): AstNode.MemberAccess<Type> {
        val expr = checkExpression(node.target)
        val type = expr.extra as? Type.Structure
            ?: throw TypeException("Cannot access member of non-structure type", node.location)
        val member = type.fields.firstOrNull { it.first == node.member }
            ?: throw TypeException("Unknown member: ${node.member}", node.location)
        return AstNode.MemberAccess(expr, node.member, node.location, member.second)
    }
}

class TypeException(message: String, location: Location) : Exception(
    "Type error at ${location.file}:${location.row}:${location.column}: $message"
)