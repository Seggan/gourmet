package io.github.seggan.gourmet.compilation.ir

sealed interface Continuation {
    data class Direct(val block: BasicBlock) : Continuation
    data class Conditional(val then: BasicBlock, val otherwise: BasicBlock) : Continuation
    data object Return : Continuation
}