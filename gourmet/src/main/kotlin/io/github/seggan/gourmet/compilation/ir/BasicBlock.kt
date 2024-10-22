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

    fun putChildren(children: MutableList<BasicBlock>) {
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
}
