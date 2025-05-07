package io.github.seggan.gourmet.compilation

import io.github.seggan.gourmet.compilation.ir.Argument
import io.github.seggan.gourmet.compilation.ir.Insn
import org.intellij.lang.annotations.Language

object PeepholeOptimizer {

    private val replacers = listOf(
        Replacer.Regex(
            """push (.+?);""",
            """(\w+) \{ nop; };""",
            replacement = "$2 $1;"
        ),
        Replacer.Regex(
            """\s*push .+?;""",
            """pop;""",
            replacement = ""
        ),
        Replacer.Regex(
            """\s*del (.+?);""",
            """def \1;""",
            replacement = ""
        ),
        Replacer.Regex(
            """\s*def (.+?);""",
            """del \1;""",
            replacement = ""
        ),
        Replacer.Regex(
            """\s*push (.+?);""",
            """pop \1;""",
            replacement = ""
        ),
        Replacer.Regex(
            """(del .+?;)""",
            """(\w+ \{ nop; };)""",
            replacement = "$2\n$1"
        ),
        Replacer.Regex(
            """pop (.+?);""",
            """push \1;""",
            """del \1;""",
            replacement = "del $1;"
        ),
        Replacer.Regex("""\s*rot 0;""", replacement = ""),
        Replacer.Function {
            val regex = listOf(
                """push (\d+?);""",
                """def (.+?);""",
                """pop \2;"""
            ).joinToString("""\n\s*""").toRegex()

            var replaced = it
            val match = regex.find(replaced)
            if (match != null) {
                val num = match.groupValues[1]
                val variable = match.groupValues[2]
                replaced = replaced.replaceRange(match.range, "def $variable;")
                var firstPop = replaced.indexOf("""pop $variable;""")
                if (firstPop == -1) {
                    firstPop = replaced.length
                }
                replaced = replaced.take(firstPop).replace(
                    """(?<!(?:def|del) )${Regex.escape(variable)}\b""".toRegex(),
                    num
                ) + replaced.drop(firstPop)
            }
            replaced
        },
        Replacer.Function {
            val regex = listOf(
                """del (.+?);""",
                """def (.+?);""",
                """pop \2;"""
            ).joinToString("""\n\s*""").toRegex()

            var replaced = it
            val match = regex.find(replaced)
            if (match != null) {
                val firstVar = Regex.escapeReplacement(match.groupValues[1])
                val secondVar = Regex.escape(match.groupValues[2])
                replaced = replaced.replace(
                    """$secondVar\b""".toRegex(),
                    firstVar
                )
            }
            replaced
        }
    )

    fun optimizeRaw(code: String): String {
        var optimized = code
        do {
            val old = optimized
            for (replacer in replacers) {
                optimized = replacer.replace(optimized)
            }
        } while (old != optimized)
        return optimized
    }

    fun optimizeBlock(insns: List<Insn>): List<Insn> {
        return localizeVariables(insns)
    }

    // Places variable definitions right before first use and variable deletions
    // right after last use so that the peephole optimizer can optimize them
    private fun localizeVariables(insns: List<Insn>): List<Insn> {
        val newInsns = insns.toMutableList()
        val variables = insns.filter { it.insn == "def" }.mapNotNull { it.args.single() as? Argument.Variable }
        for (variable in variables) {
            val def = newInsns.indexOfFirst { it.insn == "def" && it.args.single() == variable }
            val firstUse = newInsns.indexOfFirst { it.insn != "def" && it.containsVariable(variable) }
            if (firstUse != -1) {
                val defInsn = newInsns[def]
                newInsns.removeAt(def)
                newInsns.add(firstUse - 1, defInsn)
            }

            val del = newInsns.indexOfFirst { it.insn == "del" && it.args.single() == variable }
            val lastUse = newInsns.indexOfLast { it.insn != "del" && it.containsVariable(variable) }
            if (lastUse != -1) {
                val delInsn = newInsns[del]
                newInsns.removeAt(del)
                newInsns.add(lastUse + 1, delInsn)
            }
        }

        return newInsns.map {
            it.copy(
                args = it.args.map { arg ->
                    if (arg is Argument.Block) {
                        arg.copy(insns = localizeVariables(arg.insns))
                    } else {
                        arg
                    }
                }
            )
        }
    }

    private fun Insn.containsVariable(variable: Argument.Variable): Boolean {
        return variable.name in insn || args.any {
            (it is Argument.Variable && it == variable) ||
                    (it is Argument.Block && it.insns.any { insn -> insn.containsVariable(variable) })
        }
    }

    private sealed interface Replacer {
        fun replace(code: String): String

        class Regex(@Language("RegExp") vararg regex: String, val replacement: String) : Replacer {
            private val regex = regex.joinToString("""\n\s*""").toRegex()
            override fun replace(code: String): String {
                return code.replace(regex, replacement)
            }
        }

        data class Function(val func: (String) -> String) : Replacer {
            override fun replace(code: String): String {
                return func(code)
            }
        }
    }
}