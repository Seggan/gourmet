package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.compilation.Variable
import io.github.seggan.gourmet.util.randomString

data class BasicBlock(
    val insns: List<Instruction>,
    val declaredVariables: Set<Variable>,
    val droppedVariables: Set<Variable>,
    val outsideVariables: Set<Variable>,
    var continuation: Continuation? = null
) {
    val id = randomString()
}
