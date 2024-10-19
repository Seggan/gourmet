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

fun BasicBlock.toGraph(): String {
    val children = mutableListOf<BasicBlock>()
    children(children)
    val nodes = mutableSetOf<String>()
    val edges = mutableSetOf<String>()
    for (block in children) {
        val insns = block.insns.joinToString("\\n") { it.toIr() }
        val declared = block.declaredVariables.joinToString(", ") { it.name }
        val dropped = block.droppedVariables.joinToString(", ") { it.name }
        val outside = block.outsideVariables.joinToString(", ") { it.name }
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
            newline = true
            node.append("Dropped: $dropped")
        }
        if (outside.isNotEmpty()) {
            if (newline) node.append("\\n")
            node.append("Outside: $outside")
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
        appendLine("digraph G {")
        for (node in nodes) {
            appendLine(node)
        }
        for (edge in edges) {
            appendLine(edge)
        }
        appendLine("}")
    }
}

private fun BasicBlock.children(children: MutableList<BasicBlock>) {
    if (children.any { it.id == id }) return
    children.add(this)
    when (val cont = continuation) {
        is Continuation.Direct -> cont.block.children(children)
        is Continuation.Conditional -> {
            cont.then.children(children)
            cont.otherwise.children(children)
        }
        else -> return
    }
}
