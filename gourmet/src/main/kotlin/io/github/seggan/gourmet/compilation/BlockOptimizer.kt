package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.BasicBlock
import io.github.seggan.gourmet.compilation.ir.Continuation

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

    private fun BasicBlock.doPeepholeOptimization(): BasicBlock {
        if (this in visited) return this
        visited += this
        val newBlock = PeepholeOptimizer.optimizeBlock(this)
        blockMap[this.id] = newBlock
        newBlock.continuation = newBlock.continuation?.map { getLatestBlock(it.doPeepholeOptimization()) }
        return newBlock
    }

    companion object {
        fun optimize(head: BasicBlock): BasicBlock {
            return with(BlockOptimizer(head)) {
                val optimized = head.optimizeBlock()
                visited.clear()
                blockMap.clear()
                optimized.doPeepholeOptimization()
            }
        }
    }
}