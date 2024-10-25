package io.github.seggan.gourmet.typing

import io.github.seggan.gourmet.parsing.AstNode

data class TypedAst(val functions: Map<Signature, AstNode.Function<TypeData>>)
