package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.compilation.Variable
import io.github.seggan.gourmet.util.randomString

data class BasicBlock(
    val insns: List<Insn>,
    val declaredVariables: Set<Variable>,
    val droppedVariables: Set<Variable>,
    var continuation: Continuation? = null
) {
    val id = randomString()

    val children: List<BasicBlock> get() = mutableListOf<BasicBlock>().apply(::putChildren)

    fun clone() = BasicBlock(
        insns.toList(),
        declaredVariables.toSet(),
        droppedVariables.toSet(),
        continuation?.clone()
    )

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BasicBlock && other.id == id
    }

    override fun hashCode(): Int = id.hashCode()
}
