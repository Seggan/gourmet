package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.Argument
import io.github.seggan.gourmet.compilation.ir.BasicBlock
import io.github.seggan.gourmet.compilation.ir.Continuation
import io.github.seggan.gourmet.compilation.ir.Insn

class BlockOptimizer private constructor(private val head: BasicBlock) {

    private val visited = mutableSetOf<BasicBlock>()
    private val blockMap = mutableMapOf<String, BasicBlock>()

    private fun BasicBlock.optimizeBlock(): BasicBlock {
        if (this in visited) return this
        visited += this
        return when (val cont = continuation) {
            is Continuation.Direct -> {
                if (cont.block.predecessors.size == 1) {
                    val newBlock = copy(
                        insns = insns + cont.block.insns,
                        declaredVariables = declaredVariables + cont.block.declaredVariables,
                        droppedVariables = droppedVariables + cont.block.droppedVariables,
                        continuation = cont.block.continuation
                    )
                    movePredecessorsTo(newBlock).optimizeBlock()
                } else {
                    continuation = cont.copy(block = getLatestBlock(cont.block.optimizeBlock()))
                    this
                }
            }
            is Continuation.Conditional -> {
                continuation = cont.copy(
                    then = getLatestBlock(cont.then.optimizeBlock()),
                    otherwise = getLatestBlock(cont.otherwise.optimizeBlock())
                )
                this
            }
            is Continuation.Call -> {
                continuation = cont.copy(returnTo = getLatestBlock(cont.returnTo.optimizeBlock()))
                this
            }
            is Continuation.Return -> this
            null -> this
        }
    }

    private fun getLatestBlock(block: BasicBlock): BasicBlock {
        var latest = block
        while (true) {
            latest = blockMap[latest.id] ?: break
        }
        return latest
    }

    private fun BasicBlock.movePredecessorsTo(newBlock: BasicBlock): BasicBlock {
        for (pred in predecessors) {
            pred.continuation = pred.continuation?.swap(this, newBlock)
        }
        blockMap[this.id] = newBlock
        return newBlock
    }

    private val BasicBlock.predecessors: List<BasicBlock>
        get() = head.children.filter { it.continuation?.continuesTo(this) == true }

    private fun injectVariables(blocks: List<BasicBlock>) {
        val hoisted = mutableSetOf<Variable>()
        for (block in blocks) {
            for (variable in block.declaredVariables) {
                if (variable !in block.droppedVariables) {
                    hoisted.add(variable)
                }
            }
        }
        for (block in blocks) {
            val insns = block.insns.toMutableList()
            for (variable in block.declaredVariables) {
                if (variable !in hoisted) {
                    for (part in variable.mapped) {
                        insns.add(0, Insn("def", Argument.Variable(part)))
                    }
                }
            }
            for (variable in block.droppedVariables) {
                if (variable !in hoisted) {
                    for (part in variable.mapped) {
                        insns.add(Insn("del", Argument.Variable(part)))
                    }
                }
            }
            block.insns = insns
        }
    }

    companion object {
        fun optimize(head: BasicBlock): BasicBlock {
            return with(BlockOptimizer(head)) {
                val optimized = head.optimizeBlock()
                injectVariables(optimized.children)
                optimized.children.forEach { block ->
                    block.insns = PeepholeOptimizer.optimizeBlock(block.insns)
                }
                optimized
            }
        }
    }
}