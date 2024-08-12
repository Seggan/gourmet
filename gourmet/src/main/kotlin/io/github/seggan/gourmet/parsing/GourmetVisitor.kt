package io.github.seggan.gourmet.parsing

import io.github.seggan.gourmet.antlr.GourmetParser
import io.github.seggan.gourmet.antlr.GourmetParserBaseVisitor
import io.github.seggan.gourmet.util.location

object GourmetVisitor : GourmetParserBaseVisitor<AstNode<Unit>>() {

    override fun visitFile(ctx: GourmetParser.FileContext): AstNode.File<Unit> {
        return AstNode.File(ctx.function().map(::visitFunction), ctx.location, Unit)
    }

    override fun visitFunction(ctx: GourmetParser.FunctionContext): AstNode.Function<Unit> {
        val name = ctx.Identifier().text
        val args = ctx.parameter().map { it.Identifier().text to TypeName.parse(it.type()) }
        val returnType = ctx.type()?.let(TypeName::parse)
        val block = visitBlock(ctx.block())
        return AstNode.Function(name, args, returnType, block, ctx.location, Unit)
    }

    override fun visitBlock(ctx: GourmetParser.BlockContext): AstNode.Block<Unit> {
        return AstNode.Block(visitStatements(ctx.statements()), ctx.location, Unit)
    }

    override fun visitStatements(ctx: GourmetParser.StatementsContext): AstNode.Statements<Unit> {
        return AstNode.Statements(ctx.statement().map(::visit).flatMap(::flattenStatements), ctx.location, Unit)
    }

    override fun visitStatement(ctx: GourmetParser.StatementContext): AstNode.Statement<Unit> {
        return super.visitStatement(ctx) as AstNode.Statement<Unit>
    }

    override fun visitDeclaration(ctx: GourmetParser.DeclarationContext): AstNode.Statement<Unit> {
        val name = ctx.Identifier().text
        val type = ctx.type()?.let(TypeName::parse)
        val expression = ctx.expression()?.let(::visitExpression)
        return AstNode.Declaration(name, type, expression, ctx.location, Unit)
    }

    override fun visitAssignment(ctx: GourmetParser.AssignmentContext): AstNode.Statement<Unit> {
        val name = ctx.Identifier().text
        val value = visitExpression(ctx.expression())
        return AstNode.Assignment(name, value, ctx.location, Unit)
    }

    override fun visitReturn(ctx: GourmetParser.ReturnContext): AstNode.Statement<Unit> {
        val value = ctx.expression()?.let(::visitExpression)
        return AstNode.Return(value, ctx.location, Unit)
    }

    override fun visitIf(ctx: GourmetParser.IfContext): AstNode.Statement<Unit> {
        val condition = visitExpression(ctx.ifCond)
        val thenBlock = visitStatement(ctx.ifBlock)
        val elseBlock = ctx.elseBlock?.let(::visitStatement)
        return AstNode.If(condition, thenBlock, elseBlock, ctx.location, Unit)
    }

    override fun visitFor(ctx: GourmetParser.ForContext): AstNode.Statement<Unit> {
        val init = if (ctx.declaration() != null) {
            visitDeclaration(ctx.declaration())
        } else if (ctx.init != null) {
            visitAssignment(ctx.init)
        } else {
            null
        }
        val condition = ctx.cond?.let(::visitExpression) ?: AstNode.BooleanLiteral(true, ctx.location, Unit)
        var block = visitStatement(ctx.statement())
        if (ctx.update != null) {
            block = AstNode.Block(
                AstNode.Statements(listOf(block, visitAssignment(ctx.update)), block.location, Unit),
                block.location,
                Unit
            )
        }
        return AstNode.Statements(
            listOfNotNull(init, AstNode.While(condition, block, ctx.location, Unit)),
            ctx.location,
            Unit
        )
    }

    override fun visitWhile(ctx: GourmetParser.WhileContext): AstNode.Statement<Unit> {
        val condition = visitExpression(ctx.expression())
        val block = visitStatement(ctx.statement())
        return AstNode.While(condition, block, ctx.location, Unit)
    }

    override fun visitDoWhile(ctx: GourmetParser.DoWhileContext): AstNode.Statement<Unit> {
        val block = visitStatement(ctx.statement())
        val condition = visitExpression(ctx.expression())
        return AstNode.DoWhile(block, condition, ctx.location, Unit)
    }

    override fun visitExpression(ctx: GourmetParser.ExpressionContext): AstNode.Expression<Unit> {
        return when {
            ctx.primary() != null -> visitPrimary(ctx.primary())
            ctx.DOT() != null -> AstNode.MemberAccess(
                visitExpression(ctx.expression(0)),
                ctx.Identifier().text,
                ctx.location,
                Unit
            )

            ctx.prefixOp != null -> AstNode.UnaryExpression(
                UnOp.fromToken(ctx.prefixOp),
                visitExpression(ctx.expression(0)),
                ctx.location,
                Unit
            )

            ctx.op != null -> AstNode.BinaryExpression(
                visitExpression(ctx.expression(0)),
                BinOp.fromToken(ctx.op),
                visitExpression(ctx.expression(1)),
                ctx.location,
                Unit
            )

            else -> error("Unexpected expression: $ctx")
        }
    }

    override fun visitPrimary(ctx: GourmetParser.PrimaryContext): AstNode.Expression<Unit> {
        return when {
            ctx.paren != null -> visitExpression(ctx.paren)

            ctx.variable != null -> AstNode.Variable(ctx.variable.text, ctx.location, Unit)

            ctx.Number() != null -> AstNode.NumberLiteral(
                ctx.Number().text.toBigDecimal(),
                ctx.location,
                Unit
            )

            ctx.String() != null -> AstNode.StringLiteral(
                unescapeString(ctx.String().text.drop(1).dropLast(1)),
                ctx.location,
                Unit
            )

            ctx.MultilineString() != null -> AstNode.StringLiteral(
                unescapeString(ctx.MultilineString().text.drop(3).dropLast(3)),
                ctx.location,
                Unit
            )

            ctx.Char() != null -> AstNode.CharLiteral(
                unescapeString(ctx.Char().text.drop(1).dropLast(1)).single(),
                ctx.location,
                Unit
            )

            ctx.Boolean() != null -> AstNode.BooleanLiteral(
                ctx.Boolean().text.toBoolean(),
                ctx.location,
                Unit
            )

            ctx.fn != null -> AstNode.FunctionCall(
                ctx.fn.text,
                ctx.generic().type().map(TypeName::parse),
                ctx.expression().map(::visitExpression),
                ctx.location,
                Unit
            )
            else -> error("Unexpected primary: $ctx")
        }
    }
}

private fun flattenStatements(node: AstNode<Unit>): List<AstNode.Statement<Unit>> {
    return when (node) {
        is AstNode.Statements -> node.statements.flatMap(::flattenStatements)
        is AstNode.Statement -> listOf(node)
        else -> error("Unexpected node type: $node")
    }
}

private fun unescapeString(str: String): String {
    val builder = StringBuilder()
    var i = 0
    while (i < str.length) {
        val c = str[i]
        if (c == '\\') {
            val next = str[i + 1]
            builder.append(
                when (next) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '\\' -> '\\'
                    '0' -> '\u0000'
                    'u' -> {
                        val code = str.substring(i + 2, i + 6).toInt(16).toChar()
                        i += 4
                        code
                    }
                    '"' -> '"'
                    else -> next
                }
            )
            i += 2
        } else {
            builder.append(c)
            i++
        }
    }
    return builder.toString()
}