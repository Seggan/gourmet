package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.typing.Type

data class CompiledBlocks(
    val functions: Map<Type.Function, BasicBlock>,
)
