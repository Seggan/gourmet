package io.github.seggan.gourmet.typing

import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.parsing.TypeName
import io.github.seggan.gourmet.util.Location

class TypeChecker private constructor(
    private val ast: AstNode.File<Unit>,
    private val functions: MutableList<Pair<AstNode.Function<Unit>?, Signature>>
) {

    private val types = listOf(
        Type.Primitive.BOOLEAN,
        Type.Primitive.CHAR,
        Type.Primitive.NUMBER,
        Type.Unit,
        Type.Nothing
    ).associateBy { it.tname }

    private val scopes = ArrayDeque<MutableList<Pair<String, Type>>>()

    private lateinit var currentFunction: Signature

    init {
        functions += ast.functions.map { node ->
            val args = node.args.map { it.second.resolve(node.location) }
            val returnType = node.returnType?.resolve(node.location) ?: Type.Unit
            val type = Type.Function(emptyList(), args, returnType)
            val signature = Signature(node.name, type)
            node to signature
        }
    }

    private fun check(): AstNode.File<TypeData> {
        return AstNode.File(ast.functions.map(::checkFunction), ast.location, TypeData.Empty)
    }

    private fun checkFunction(node: AstNode.Function<Unit>): AstNode.Function<TypeData> {
        currentFunction = functions.first { it.first == node }.second
        val args = node.args.unzip().first.zip(currentFunction.type.args)
        scopes.addFirst(args.toMutableList())
        val block = checkBlock(node.body)
        scopes.removeFirst()
        return AstNode.Function(
            node.attributes,
            node.name,
            node.args,
            node.returnType,
            block,
            node.location,
            TypeData.Basic(currentFunction.type)
        )
    }

    private fun checkBlock(node: AstNode.Block<Unit>): AstNode.Block<TypeData> {
        scopes.addFirst(mutableListOf())
        val statements = node.statements.map(::checkStatement)
        scopes.removeFirst()
        return AstNode.Block(statements, node.location, TypeData.Empty)
    }

    private fun checkStatement(node: AstNode.Statement<Unit>): AstNode.Statement<TypeData> {
        return when (node) {
            is AstNode.Declaration -> checkDeclaration(node)
            is AstNode.Assignment -> checkAssignment(node)
            is AstNode.Return -> checkReturn(node)
            is AstNode.Block -> checkBlock(node)
            is AstNode.If -> checkIf(node)
            is AstNode.DoWhile -> checkDoWhile(node)
            is AstNode.While -> checkWhile(node)
            is AstNode.For -> checkFor(node)
            is AstNode.Expression -> checkExpression(node)
        }
    }

    private fun checkDeclaration(node: AstNode.Declaration<Unit>): AstNode.Declaration<TypeData> {
        val value = node.value?.let(::checkExpression)
        val type = node.type?.resolve(node.location)
            ?: value?.realType
            ?: throw TypeException("Cannot infer type of declaration", node.location)
        if (value != null && !value.realType.isAssignableTo(type)) {
            throw TypeException("Cannot assign ${value.extra} to $type", node.location)
        }
        scopes.first().add(node.name to type)
        return AstNode.Declaration(node.name, node.type, value, node.location, TypeData.Basic(type))
    }

    private fun checkAssignment(node: AstNode.Assignment<Unit>): AstNode.Assignment<TypeData> {
        val value = checkExpression(node.value)
        val type = findVariable(node.name, node.location)
        val assignOp = node.assignType.op
        if (assignOp == null) {
            if (!value.realType.isAssignableTo(type)) {
                throw TypeException("Cannot assign ${value.extra} to $type", node.location)
            }
        } else {
            assignOp.checkType(type, value.realType, node.location)
        }
        return AstNode.Assignment(node.name, node.assignType, value, node.location, TypeData.Basic(type))
    }

    private fun checkReturn(node: AstNode.Return<Unit>): AstNode.Return<TypeData> {
        val value = node.value?.let(::checkExpression)
        val valueType = value?.realType ?: Type.Unit
        val returnType = currentFunction.type.returnType
        if (!valueType.isAssignableTo(returnType)) {
            throw TypeException(
                "Cannot return $valueType from function with return type $returnType",
                node.location
            )
        }
        return AstNode.Return(value, node.location, TypeData.Basic(returnType))
    }

    private fun checkIf(node: AstNode.If<Unit>): AstNode.If<TypeData> {
        val condition = checkExpression(node.condition)
        if (!condition.realType.isAssignableTo(Type.Primitive.BOOLEAN)) {
            throw TypeException("Condition must be a boolean", condition.location)
        }
        val thenBlock = checkStatement(node.thenBody)
        val elseBlock = node.elseBody?.let(::checkStatement)
        return AstNode.If(condition, thenBlock, elseBlock, node.location, TypeData.Empty)
    }

    private fun checkWhile(node: AstNode.While<Unit>): AstNode.While<TypeData> {
        val condition = checkExpression(node.condition)
        if (!condition.realType.isAssignableTo(Type.Primitive.BOOLEAN)) {
            throw TypeException("Condition must be a boolean", condition.location)
        }
        val block = checkStatement(node.body)
        return AstNode.While(condition, block, node.location, TypeData.Empty)
    }

    private fun checkDoWhile(node: AstNode.DoWhile<Unit>): AstNode.DoWhile<TypeData> {
        val block = checkStatement(node.body)
        val condition = checkExpression(node.condition)
        if (!condition.realType.isAssignableTo(Type.Primitive.BOOLEAN)) {
            throw TypeException("Condition must be a boolean", condition.location)
        }
        return AstNode.DoWhile(block, condition, node.location, TypeData.Empty)
    }

    private fun checkFor(node: AstNode.For<Unit>): AstNode.For<TypeData> {
        val init = node.init?.let(::checkStatement)
        val condition = checkExpression(node.condition)
        if (!condition.realType.isAssignableTo(Type.Primitive.BOOLEAN)) {
            throw TypeException("Condition must be a boolean", condition.location)
        }
        val update = node.update?.let(::checkStatement)
        val block = checkStatement(node.body)
        return AstNode.For(init, condition, update, block, node.location, TypeData.Empty)
    }

    private fun checkExpression(node: AstNode.Expression<Unit>): AstNode.Expression<TypeData> {
        return when (node) {
            is AstNode.BooleanLiteral -> AstNode.BooleanLiteral(
                node.value,
                node.location,
                TypeData.Basic(Type.Primitive.BOOLEAN)
            )

            is AstNode.CharLiteral -> AstNode.CharLiteral(
                node.value,
                node.location,
                TypeData.Basic(Type.Primitive.CHAR)
            )

            is AstNode.NumberLiteral -> AstNode.NumberLiteral(
                node.value,
                node.location,
                TypeData.Basic(Type.Primitive.NUMBER)
            )

            is AstNode.StringLiteral -> AstNode.StringLiteral(
                node.value,
                node.location,
                TypeData.Basic(Type.STRING)
            )

            is AstNode.Variable -> AstNode.Variable(
                node.name,
                node.location,
                TypeData.Basic(findVariable(node.name, node.location))
            )

            is AstNode.BinaryExpression -> checkBinaryExpression(node)
            is AstNode.UnaryExpression -> checkUnaryExpression(node)
            is AstNode.FunctionCall -> checkFunctionCall(node)
            is AstNode.MemberAccess -> checkMemberAccess(node)
        }
    }

    private fun checkBinaryExpression(node: AstNode.BinaryExpression<Unit>): AstNode.BinaryExpression<TypeData> {
        val left = checkExpression(node.left)
        val right = checkExpression(node.right)
        val type = node.operator.checkType(left.realType, right.realType, node.location)
        return AstNode.BinaryExpression(left, node.operator, right, node.location, TypeData.Basic(type))
    }

    private fun checkUnaryExpression(node: AstNode.UnaryExpression<Unit>): AstNode.UnaryExpression<TypeData> {
        val value = checkExpression(node.value)
        val type = node.operator.checkType(value.realType, node.location)
        return AstNode.UnaryExpression(node.operator, value, node.location, TypeData.Basic(type))
    }

    private fun checkFunctionCall(node: AstNode.FunctionCall<Unit>): AstNode.FunctionCall<TypeData> {
        var exception = TypeException("Unknown function: ${node.name}", node.location)
        outer@ for ((_, function) in functions) {
            if (function.name != node.name) continue
            var type = function.type
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
                if (!provided.realType.isAssignableTo(arg)) {
                    exception = TypeException("Cannot assign ${provided.extra} to $arg", provided.location)
                    continue@outer
                }
            }
            return AstNode.FunctionCall(
                node.name,
                node.genericArgs,
                args,
                node.location,
                TypeData.FunctionCall(type.returnType, function)
            )
        }
        throw exception
    }

    private fun checkMemberAccess(node: AstNode.MemberAccess<Unit>): AstNode.MemberAccess<TypeData> {
        val expr = checkExpression(node.target)
        val type = expr.realType as? Type.Structure
            ?: throw TypeException("Cannot access member of non-structure type", node.location)
        val member = type.fields.firstOrNull { it.first == node.member }
            ?: throw TypeException("Unknown member: ${node.member}", node.location)
        return AstNode.MemberAccess(expr, node.member, node.location, TypeData.Basic(member.second))
    }

    private fun TypeName.resolve(location: Location): Type {
        return when (this) {
            is TypeName.Simple -> types[name] ?: throw TypeException("Unknown type: $this", location)
            is TypeName.Pointer -> Type.Pointer(type.resolve(location))
            is TypeName.Generic -> Type.Generic(name)
        }
    }

    private fun findVariable(name: String, location: Location): Type {
        for (scope in scopes) {
            for ((n, t) in scope) {
                if (n == name) {
                    return t
                }
            }
        }
        throw TypeException("Unknown variable: $name", location)
    }

    companion object {
        fun check(ast: AstNode.File<Unit>, external: List<Signature> = emptyList()): AstNode.File<TypeData> {
            return TypeChecker(ast, external.map { null to it }.toMutableList()).check()
        }
    }
}

class TypeException(message: String, location: Location) : Exception(
    "Type error at ${location.file}:${location.row}:${location.column}: $message"
)