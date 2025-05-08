package io.github.seggan.gourmet.compilation.ir

import io.github.seggan.gourmet.compilation.optimization.PeepholeOptimizer
import io.github.seggan.gourmet.typing.Signature
import io.github.seggan.gourmet.util.randomString

data class CompiledFunction(
    val signature: Signature,
    val attributes: Set<String>,
    val body: BasicBlock
)

fun CompiledFunction.toGraph(): String {
    val returnName = randomString()
    val children = body.children
    val nodes = mutableSetOf<String>()
    val edges = mutableSetOf<String>()
    for (block in children) {
        val insns = PeepholeOptimizer.optimizeRaw(block.insns.joinToString("\n") { it.toIr() }).replace("\n", "\\n")
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
        node.append("\" shape=box];")
        nodes.add(node.toString())
        when (val cont = block.continuation) {
            is Continuation.Direct -> edges.add("${block.id} -> ${cont.block.id};")
            is Continuation.Conditional -> {
                edges.add("${block.id} -> ${cont.then.id} [label=true color=green];")
                edges.add("${block.id} -> ${cont.otherwise.id} [label=false color=red];")
            }

            is Continuation.Call -> edges.add("${block.id} -> ${cont.returnTo.id} [label=\"call ${cont.function}\"];")
            is Continuation.Return -> edges.add("${block.id} -> $returnName;")
            null -> {}
        }
    }

    return buildString {
        appendLine("subgraph ${signature.name} {")
        appendLine("rankdir=LR;")
        appendLine("""${signature.hashCode()} [label="$signature"];""")
        appendLine("""$returnName [shape=plaintext, label=return];""")
        for (node in nodes) {
            appendLine(node)
        }
        appendLine("""${signature.hashCode()} -> ${body.id};""")
        for (edge in edges) {
            appendLine(edge)
        }
        appendLine("}")
    }
}
