package io.github.seggan.gourmet.typing

import io.github.seggan.gourmet.compilation.CompiletimeFunction
import io.github.seggan.gourmet.parsing.AstNode
import io.github.seggan.gourmet.parsing.TypeName
import io.github.seggan.gourmet.util.Location

class TypeChecker private constructor(
    private val signature: Signature,
    private val structs: Set<Type.Structure>,
    private val functionMap: Map<AstNode.Function<Location>, Signature>,
    private val checked: MutableMap<Signature, AstNode.Function<TypeData>>,
    private val generics: Map<Type, Type>
) {

    private val functions = functionMap.values + CompiletimeFunction.entries.map { it.signature } + signature
    private val scopes = ArrayDeque<MutableList<Pair<String, Type>>>()

    private fun check(node: AstNode.Function<Location>) {
        val args = node.args.unzip().first.zip(signature.type.args)
        scopes.addFirst(args.toMutableList())
        val block = checkBlock(node.body)
        scopes.removeFirst()
        checked += signature to AstNode.Function(
            node.attributes,
            node.name,
            node.genericArgs,
            node.args,
            node.returnType,
            block,
            TypeData.Basic(signature.type, node.extra)
        )
    }

    private fun checkBlock(node: AstNode.Block<Location>): AstNode.Block<TypeData> {
        scopes.addFirst(mutableListOf())
        val statements = node.statements.map(::checkStatement)
        scopes.removeFirst()
        return AstNode.Block(statements, TypeData.Empty(node.extra))
    }

    private fun checkStatement(node: AstNode.Statement<Location>): AstNode.Statement<TypeData> {
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

    private fun checkDeclaration(node: AstNode.Declaration<Location>): AstNode.Declaration<TypeData> {
        val value = node.value?.let(::checkExpression)
        val type = node.type?.resolve(node.extra, structs, generics)
            ?: value?.realType
            ?: throw TypeException("Cannot infer type of declaration", node.extra)
        if (value != null && !value.realType.isAssignableTo(type)) {
            throw TypeException("Cannot assign ${value.realType} to $type", node.extra)
        }
        scopes.first().add(node.name to type)
        return AstNode.Declaration(node.name, node.type, value, TypeData.Basic(type, node.extra))
    }

    private fun checkAssignment(node: AstNode.Assignment<Location>): AstNode.Assignment<TypeData> {
        val value = checkExpression(node.value)
        var type = findVariable(node.name, node.extra)
        if (node.isPointer) {
            if (type is Type.Pointer) {
                type = type.target
            } else {
                throw TypeException("Cannot assign to non-pointer type", node.extra)
            }
        }
        val assignOp = node.assignType.op
        if (assignOp == null) {
            if (!value.realType.isAssignableTo(type)) {
                throw TypeException("Cannot assign ${value.realType} to $type", node.extra)
            }
        } else {
            assignOp.checkType(type, value.realType, node.extra)
        }
        return AstNode.Assignment(
            node.isPointer,
            node.name,
            node.assignType,
            value,
            TypeData.Empty(node.extra)
        )
    }

    private fun checkReturn(node: AstNode.Return<Location>): AstNode.Return<TypeData> {
        val value = node.value?.let(::checkExpression)
        val valueType = value?.realType ?: Type.Unit
        val returnType = signature.type.returnType
        if (!valueType.isAssignableTo(returnType)) {
            throw TypeException(
                "Cannot return $valueType from function with return type $returnType",
                node.extra
            )
        }
        return AstNode.Return(value, TypeData.Basic(returnType, node.extra))
    }

    private fun checkIf(node: AstNode.If<Location>): AstNode.If<TypeData> {
        val condition = checkExpression(node.condition)
        if (!condition.realType.isAssignableTo(Type.Primitive.BOOLEAN)) {
            throw TypeException("Condition must be a boolean", condition.extra.location)
        }
        val thenBlock = checkStatement(node.thenBody)
        val elseBlock = node.elseBody?.let(::checkStatement)
        return AstNode.If(condition, thenBlock, elseBlock, TypeData.Empty(node.extra))
    }

    private fun checkWhile(node: AstNode.While<Location>): AstNode.While<TypeData> {
        val condition = checkExpression(node.condition)
        if (!condition.realType.isAssignableTo(Type.Primitive.BOOLEAN)) {
            throw TypeException("Condition must be a boolean", condition.extra.location)
        }
        val block = checkStatement(node.body)
        return AstNode.While(condition, block, TypeData.Empty(node.extra))
    }

    private fun checkDoWhile(node: AstNode.DoWhile<Location>): AstNode.DoWhile<TypeData> {
        val block = checkStatement(node.body)
        val condition = checkExpression(node.condition)
        if (!condition.realType.isAssignableTo(Type.Primitive.BOOLEAN)) {
            throw TypeException("Condition must be a boolean", condition.extra.location)
        }
        return AstNode.DoWhile(block, condition, TypeData.Empty(node.extra))
    }

    private fun checkFor(node: AstNode.For<Location>): AstNode.For<TypeData> {
        val init = node.init?.let(::checkStatement)
        val condition = checkExpression(node.condition)
        if (!condition.realType.isAssignableTo(Type.Primitive.BOOLEAN)) {
            throw TypeException("Condition must be a boolean", condition.extra.location)
        }
        val update = node.update?.let(::checkStatement)
        val block = checkStatement(node.body)
        return AstNode.For(init, condition, update, block, TypeData.Empty(node.extra))
    }

    private fun checkExpression(node: AstNode.Expression<Location>): AstNode.Expression<TypeData> {
        return when (node) {
            is AstNode.BooleanLiteral -> AstNode.BooleanLiteral(
                node.value,
                TypeData.Basic(Type.Primitive.BOOLEAN, node.extra)
            )

            is AstNode.CharLiteral -> AstNode.CharLiteral(
                node.value,
                TypeData.Basic(Type.Primitive.CHAR, node.extra)
            )

            is AstNode.NumberLiteral -> AstNode.NumberLiteral(
                node.value,
                TypeData.Basic(Type.Primitive.NUMBER, node.extra)
            )

            is AstNode.StringLiteral -> AstNode.StringLiteral(
                node.value,
                TypeData.Basic(Type.STRING, node.extra)
            )

            is AstNode.Variable -> AstNode.Variable(
                node.name,
                TypeData.Basic(findVariable(node.name, node.extra), node.extra)
            )

            is AstNode.BinaryExpression -> checkBinaryExpression(node)
            is AstNode.UnaryExpression -> checkUnaryExpression(node)
            is AstNode.FunctionCall -> checkFunctionCall(node)
            is AstNode.MemberAccess -> checkMemberAccess(node)
            is AstNode.StructInstance -> checkStructInstance(node)
        }
    }

    private fun checkBinaryExpression(node: AstNode.BinaryExpression<Location>): AstNode.BinaryExpression<TypeData> {
        val left = checkExpression(node.left)
        val right = checkExpression(node.right)
        val type = node.operator.checkType(left.realType, right.realType, node.extra)
        return AstNode.BinaryExpression(left, node.operator, right, TypeData.Basic(type, node.extra))
    }

    private fun checkUnaryExpression(node: AstNode.UnaryExpression<Location>): AstNode.UnaryExpression<TypeData> {
        val value = checkExpression(node.value)
        val type = node.operator.checkType(value.realType, node.extra)
        return AstNode.UnaryExpression(node.operator, value, TypeData.Basic(type, node.extra))
    }

    private fun checkFunctionCall(node: AstNode.FunctionCall<Location>): AstNode.FunctionCall<TypeData> {
        var exception = TypeException("Unknown function: ${node.name}", node.extra)
        outer@ for (function in functions) {
            if (function.name != node.name) continue
            var type = function.type
            if (node.genericArgs.size != type.genericArgs.size) {
                exception = TypeException(
                    "Expected ${type.genericArgs.size} generic arguments, got ${node.genericArgs.size}",
                    node.extra
                )
                continue
            }
            if (node.args.size != type.args.size) {
                exception = TypeException("Expected ${type.args.size} arguments, got ${node.args.size}", node.extra)
                continue
            }
            val genericMap = type.genericArgs
                .zip(node.genericArgs.map { it.resolve(node.extra, structs, generics) })
                .toMap()
            type = type.fillGenerics(genericMap) as Type.Function
            if (type.genericArgs.any { it is Type.Generic }) {
                exception = TypeException("Generic arguments not fully resolved", node.extra)
                continue
            }
            val args = node.args.map(::checkExpression)
            for ((arg, provided) in type.args.zip(args)) {
                val providedType = provided.realType
                if (!providedType.isAssignableTo(arg)) {
                    exception = TypeException("Cannot pass $providedType to $arg", provided.extra.location)
                    continue@outer
                }
            }
            if (function !in CompiletimeFunction.signatures) {
                val signature = function.copy(type = type)
                if (signature !in functions && checked.keys.all { it.type != type }) {
                    // monomorphize the function
                    TypeChecker(
                        signature,
                        structs,
                        functionMap,
                        checked,
                        genericMap
                    ).check(functionMap.entries.first { it.value == function }.key)
                }
            }
            return AstNode.FunctionCall(
                node.name,
                node.genericArgs,
                args,
                TypeData.FunctionCall(type.returnType, type, function, node.extra)
            )
        }
        throw exception
    }

    private fun checkMemberAccess(node: AstNode.MemberAccess<Location>): AstNode.MemberAccess<TypeData> {
        val expr = checkExpression(node.target)
        val type = expr.realType as? Type.Structure
            ?: throw TypeException("Cannot access member of non-structure type", node.extra)
        val member = type.fields.firstOrNull { it.first == node.member }
            ?: throw TypeException("Unknown member: ${node.member}", node.extra)
        return AstNode.MemberAccess(expr, node.member, TypeData.Basic(member.second, node.extra))
    }

    private fun checkStructInstance(node: AstNode.StructInstance<Location>): AstNode.StructInstance<TypeData> {
        val type = node.type.resolve(node.extra, structs, generics)
        if (type !is Type.Structure) {
            throw TypeException("Cannot create instance of non-structure type", node.extra)
        }
        if (node.values.size != type.fields.size) {
            throw TypeException(
                "Expected ${type.fields.size} fields, got ${node.values.size}",
                node.extra
            )
        }
        val values = node.values.map { (name, value) ->
            val typedValue = checkExpression(value)
            val valueType = typedValue.realType
            val fieldType = type.fields.first { it.first == name }.second
            if (!valueType.isAssignableTo(fieldType)) {
                throw TypeException("Cannot assign $valueType to $fieldType", value.extra)
            }
            name to typedValue
        }
        return AstNode.StructInstance(node.type, values, TypeData.Basic(type, node.extra))
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
        fun check(file: AstNode.File<Location>): TypedAst {
            val structs = mutableSetOf<Type.Structure>()
            for (node in file.structs) {
                val genericArgs: List<Type> = node.generics.map { Type.Generic(it) }
                val genericMap = genericArgs.associateWith { it }
                val fields = node.fields.map { (name, typeName) ->
                    name to typeName.resolve(node.extra, structs, genericMap)
                }
                structs += Type.Structure(node.name, genericArgs, fields)
            }
            val functionMap = file.functions.associateWith { node ->
                val genericArgs: List<Type> = node.genericArgs.map { Type.Generic(it) }
                val genericMap = genericArgs.associateWith { it }
                val args = node.args.map { (_, type) -> type.resolve(node.extra, structs, genericMap) }
                val returnType = node.returnType?.resolve(node.extra, structs, genericMap) ?: Type.Unit
                Signature(node.name, Type.Function(genericArgs, args, returnType))
            }
            val checked = mutableMapOf<Signature, AstNode.Function<TypeData>>()
            for ((function, signature) in functionMap) {
                if (signature.type.genericArgs.any { it is Type.Generic }) continue
                TypeChecker(signature, structs, functionMap, checked, emptyMap()).check(function)
            }
            return TypedAst(checked)
        }
    }
}

class TypeException(message: String, location: Location) : Exception(
    "Type error at ${location.file}:${location.row}:${location.column}: $message"
)

private val types = listOf(
    Type.Primitive.BOOLEAN,
    Type.Primitive.CHAR,
    Type.Primitive.NUMBER,
    Type.Unit,
    Type.Nothing,
    Type.STRING
).associateBy { it.tname }

private fun TypeName.resolve(
    location: Location,
    structs: Set<Type.Structure>,
    genericMap: Map<Type, Type>
): Type {
    return when (this) {
        is TypeName.Simple -> types[name]
            ?: structs.firstOrNull { it.tname == name && it.generics.isEmpty() }
            ?: genericMap[Type.Generic(name)]

        is TypeName.Pointer -> Type.Pointer(type.resolve(location, structs, genericMap))
        is TypeName.Generic -> structs.firstOrNull {
            it.tname == name && it.generics.size == generics.size
        }?.let { struct ->
            val genericArgs = generics.map { it.resolve(location, structs, genericMap) }
            struct.fillGenerics(struct.generics.zip(genericArgs).toMap())
        }
    } ?: throw TypeException(
        "Unknown type: $name",
        location
    )
}

private fun Type.fillGenerics(generics: Map<Type, Type>): Type {
    return generics.entries.fold(this) { acc, (generic, provided) ->
        acc.fillGeneric(generic, provided)
    }
}
