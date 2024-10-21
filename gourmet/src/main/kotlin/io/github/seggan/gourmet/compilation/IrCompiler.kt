package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.BasicBlock
import io.github.seggan.gourmet.compilation.ir.CompiledFunction
import io.github.seggan.gourmet.compilation.ir.Continuation

class IrCompiler private constructor(private val functions: List<CompiledFunction>) {

    private val allBlocks = functions.flatMap {
        val children = mutableListOf<BasicBlock>()
        it.body.putChildren(children)
        children
    }

    private val blockStates = allBlocks.withIndex().associate { (i, v) -> v.id to i + 1 }

    private val hoisted = mutableSetOf<Variable>()

    private fun compile(): String {
        for (block in allBlocks) {
            for (variable in block.declaredVariables) {
                if (variable !in block.droppedVariables) {
                    hoisted.add(variable)
                }
            }
        }
        val blocks = allBlocks.map(::compileBlock)
        val entry = functions.firstOrNull { "entry" in it.attributes }
            ?: throw CompilationException("No entry function found")
        val sb = StringBuilder()
        sb.appendLine("def \$state ${entry.body.state};")
        sb.appendLine("def @returns;")
        for (variable in hoisted) {
            for (part in variable.mapped) {
                sb.appendLine("def $$part;")
            }
        }
        sb.appendLine("@returns.push 0;")
        sb.appendLine("while \$state {")
        for (block in blocks) {
            sb.appendLine(block.lines().joinToString("\n") { "  $it" }.trimEnd())
        }
        sb.appendLine("};")
        return sb.toString()
    }

    private fun compileBlock(block: BasicBlock): String {
        val sb = StringBuilder()
        for (variable in block.declaredVariables.filterNot { it in hoisted }) {
            for (part in variable.mapped) {
                sb.appendLine("def $$part;")
            }
        }
        for (insn in block.insns) {
            sb.appendLine(insn.toIr().trimEnd())
        }
        for (variable in block.droppedVariables.filterNot { it in hoisted }) {
            for (part in variable.mapped) {
                sb.appendLine("del $$part;")
            }
        }
        when (val cont = block.continuation) {
            is Continuation.Conditional -> {
                sb.appendLine("def \$cond;")
                sb.appendLine("pop \$cond;")
                sb.appendLine("push \$cond;")
                sb.appendLine("mul ${cont.then.state};")
                sb.appendLine("push 1;")
                sb.appendLine("sub \$cond;")
                sb.appendLine("mul ${cont.otherwise.state};")
                sb.appendLine("add { nop; };")
                sb.appendLine("pop \$state;")
                sb.appendLine("del \$cond;")
            }

            is Continuation.Direct -> {
                sb.appendLine("push ${cont.block.state};")
                sb.appendLine("pop \$state;")
            }

            is Continuation.Return -> {
                sb.appendLine("@returns.pop \$state;")
            }
            null -> throw CompilationException("Block has no continuation")
        }

        val fullBlock = StringBuilder()
        fullBlock.appendLine("push \$state;")
        fullBlock.appendLine("eq ${block.state};")
        fullBlock.appendLine("if {")
        fullBlock.appendLine(sb.lines().joinToString("\n") { "  $it" }.trimEnd())
        fullBlock.appendLine("};")
        return fullBlock.toString()
    }

    private val BasicBlock.state get() = blockStates[id]!!

    companion object {
        fun compile(functions: List<CompiledFunction>): String {
            return IrCompiler(functions).compile()
        }
    }
}