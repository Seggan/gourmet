package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.typing.Type

data class CompiledFunction(
    val attributes: Set<String>,
    val name: String,
    val type: Type.Function,
    val body: BasicBlock
)

fun CompiledFunction.toGraph(): String {
    val children = mutableListOf<BasicBlock>()
    body.putChildren(children)
    val nodes = mutableSetOf<String>()
    val edges = mutableSetOf<String>()
    for (block in children) {
        val insns = block.insns.joinToString("\\n") { it.toIr().trimEnd() }
        val declared = block.declaredVariables.joinToString(", ") { it.name }
        val dropped = block.droppedVariables.joinToString(", ") { it.name }
        val node = StringBuilder()
        node.append("${block.id} [label=\"")
        var newline = false
        if (insns.isNotEmpty()) {
            node.append(insns)
            newline = true
        }
        if (declared.isNotEmpty()) {
            if (newline) node.append("\\n")
            newline = true
            node.append("Declared: $declared")
        }
        if (dropped.isNotEmpty()) {
            if (newline) node.append("\\n")
            node.append("Dropped: $dropped")
        }
        node.append("\"];")
        nodes.add(node.toString())
        when (val cont = block.continuation) {
            is Continuation.Direct -> edges.add("${block.id} -> ${cont.block.id};")
            is Continuation.Conditional -> {
                edges.add("${block.id} -> ${cont.then.id} [label=\"true\"];")
                edges.add("${block.id} -> ${cont.otherwise.id} [label=\"false\"];")
            }

            is Continuation.Return -> edges.add("${block.id} -> return;")
            null -> {}
        }
    }

    return buildString {
        appendLine("digraph $name {")
        appendLine("rankdir=LR;")
        appendLine("""$name [label="$name ${type.tname}", shape=box];""")
        appendLine("""return [shape=plaintext];""")
        for (node in nodes) {
            appendLine(node)
        }
        appendLine("""$name -> ${body.id};""")
        for (edge in edges) {
            appendLine(edge)
        }
        appendLine("}")
    }
}
