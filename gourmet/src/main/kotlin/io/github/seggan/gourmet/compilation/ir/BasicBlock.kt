package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.compilation.Variable
import io.github.seggan.gourmet.util.randomString

data class BasicBlock(
    var insns: List<Insn>,
    val declaredVariables: Set<Variable>,
    val droppedVariables: Set<Variable>,
    var continuation: Continuation? = null
) {
    val id = randomString()

    val children: List<BasicBlock> get() = mutableListOf<BasicBlock>().apply(::putChildren)

    private fun putChildren(children: MutableList<BasicBlock>) {
        if (children.any { it.id == id }) return
        children.add(this)
        when (val cont = continuation) {
            is Continuation.Direct -> cont.block.putChildren(children)
            is Continuation.Conditional -> {
                cont.then.putChildren(children)
                cont.otherwise.putChildren(children)
            }

            is Continuation.Call -> cont.returnTo.putChildren(children)
            is Continuation.Return -> {}
            null -> {}
        }
    }

    fun clone(): BasicBlock {
        val children = children.associateBy { it.id }
        val oldToNew = mutableMapOf<String, String>()
        val newChildren = mutableMapOf<String, BasicBlock>()
        for ((id, child) in children) {
            val new = BasicBlock(
                child.insns,
                child.declaredVariables,
                child.droppedVariables
            ) // hehe immutable data structures go brr
            oldToNew[id] = new.id
            newChildren[new.id] = new
        }
        for ((id, child) in children) {
            val cont = child.continuation
            val newChild = newChildren[oldToNew[id]!!]!!
            newChild.continuation = when (cont) {
                is Continuation.Direct -> cont.copy(block = newChildren[oldToNew[cont.block.id]!!]!!)
                is Continuation.Conditional -> cont.copy(
                    then = newChildren[oldToNew[cont.then.id]!!]!!,
                    otherwise = newChildren[oldToNew[cont.otherwise.id]!!]!!
                )

                is Continuation.Call -> cont.copy(returnTo = newChildren[oldToNew[cont.returnTo.id]!!]!!)
                is Continuation.Return -> cont
                null -> null
            }
        }
        return newChildren[oldToNew[this.id]!!]!!
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BasicBlock && other.id == id
    }

    override fun hashCode(): Int = id.hashCode()
}
