package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.typing.Signature

sealed interface Continuation {

    data class Direct(val block: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return if (previous === block) Direct(new) else this
        }

        override fun continuesTo(block: BasicBlock) = this.block === block

        override fun map(mapping: (BasicBlock) -> BasicBlock): Continuation {
            return Direct(mapping(block))
        }

        override fun toString(): String {
            return "Direct(block=${block.id})"
        }
    }

    data class Conditional(val then: BasicBlock, val otherwise: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return Conditional(
                then = if (previous === then) new else then,
                otherwise = if (previous === otherwise) new else otherwise
            )
        }

        override fun continuesTo(block: BasicBlock) = then === block || otherwise === block

        override fun map(mapping: (BasicBlock) -> BasicBlock): Continuation {
            return Conditional(mapping(then), mapping(otherwise))
        }

        override fun toString(): String {
            return "Conditional(then=${then.id}, otherwise=${otherwise.id})"
        }
    }

    data class Call(val function: Signature, val returnTo: BasicBlock) : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation {
            return if (previous === returnTo) Call(function, new) else this
        }

        override fun continuesTo(block: BasicBlock) = returnTo === block

        override fun map(mapping: (BasicBlock) -> BasicBlock): Continuation {
            return Call(function, mapping(returnTo))
        }

        override fun toString(): String {
            return "Call(function=$function, returnTo=${returnTo.id})"
        }
    }

    data object Return : Continuation {
        override fun swap(previous: BasicBlock, new: BasicBlock): Continuation = this
        override fun continuesTo(block: BasicBlock) = false
        override fun map(mapping: (BasicBlock) -> BasicBlock): Continuation = this
    }

    fun swap(previous: BasicBlock, new: BasicBlock): Continuation
    fun continuesTo(block: BasicBlock): Boolean
    fun map(mapping: (BasicBlock) -> BasicBlock): Continuation
}