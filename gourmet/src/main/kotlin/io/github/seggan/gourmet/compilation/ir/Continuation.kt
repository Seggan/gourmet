package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.compilation.Signature

sealed interface Continuation {

    data class Direct(val block: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return if (previous === block) Direct(new) else this
        }

        override fun continuesTo(block: BasicBlock) = this.block === block
    }

    data class Conditional(val then: BasicBlock, val otherwise: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return Conditional(
                then = if (previous === then) new else then,
                otherwise = if (previous === otherwise) new else otherwise
            )
        }

        override fun continuesTo(block: BasicBlock) = then === block || otherwise === block
    }

    data class Call(val function: Signature, val returnTo: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return if (previous === returnTo) Call(function, new) else this
        }

        override fun continuesTo(block: BasicBlock) = returnTo === block
    }

    data object Return : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation = this
        override fun continuesTo(block: BasicBlock) = false
    }

    fun swap(previous: BasicBlock, new: BasicBlock): Continuation
    fun continuesTo(block: BasicBlock): Boolean
}