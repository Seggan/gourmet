package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.typing.Signature

sealed interface Continuation {

    data class Direct(val block: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return if (previous === block) Direct(new) else this
        }

        override fun continuesTo(block: BasicBlock) = this.block === block

        override fun clone(): Continuation = Direct(block.clone())
    }

    data class Conditional(val then: BasicBlock, val otherwise: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return Conditional(
                then = if (previous === then) new else then,
                otherwise = if (previous === otherwise) new else otherwise
            )
        }

        override fun continuesTo(block: BasicBlock) = then === block || otherwise === block

        override fun clone(): Continuation = Conditional(then.clone(), otherwise.clone())
    }

    data class Call(val function: Signature, val returnTo: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return if (previous === returnTo) Call(function, new) else this
        }

        override fun continuesTo(block: BasicBlock) = returnTo === block

        override fun clone(): Continuation = Call(function, returnTo.clone())
    }

    data object Return : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation = this
        override fun continuesTo(block: BasicBlock) = false
        override fun clone(): Continuation = this
    }

    fun swap(previous: BasicBlock, new: BasicBlock): Continuation
    fun continuesTo(block: BasicBlock): Boolean
    fun clone(): Continuation
}