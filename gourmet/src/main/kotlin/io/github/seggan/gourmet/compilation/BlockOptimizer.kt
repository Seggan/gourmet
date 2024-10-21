package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.BasicBlock
import io.github.seggan.gourmet.compilation.ir.Continuation

class BlockOptimizer private constructor(private val head: BasicBlock) {

    private val visited = mutableSetOf<BasicBlock>()

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
                    cont.block.optimizeBlock()
                }
            }
            is Continuation.Conditional -> {
                val newCont = cont.copy(
                    then = cont.then.optimizeBlock(),
                    otherwise = cont.otherwise.optimizeBlock()
                )
                continuation = newCont
                this
            }
            is Continuation.Return -> this
            null -> this
        }
    }

    private fun BasicBlock.movePredecessorsTo(newBlock: BasicBlock): BasicBlock {
        for (pred in predecessors) {
            pred.continuation = pred.continuation?.swap(this, newBlock)
        }
        return newBlock
    }

    private val BasicBlock.predecessors: List<BasicBlock>
        get() = mutableListOf<BasicBlock>().apply(head::putChildren)
            .filter { it.continuation?.continuesTo(this) == true }

    companion object {
        fun optimize(head: BasicBlock): BasicBlock {
            val newHead = with(BlockOptimizer(head)) { head.optimizeBlock() }
            return newHead
        }
    }
}