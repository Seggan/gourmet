package io.github.seggan.gourmet.compilation.optimization

import io.github.seggan.gourmet.compilation.ir.Argument
import io.github.seggan.gourmet.compilation.ir.Insn
import org.intellij.lang.annotations.Language

object PeepholeOptimizer {

    private val replacers = listOf(
        Replacer.RegexReplace(
            """push (.+?);""",
            """(\w+) \{ nop; };""",
            replacement = "$2 $1;"
        ),
        Replacer.RegexReplace(
            """\s*push .+?;""",
            """pop;""",
            replacement = ""
        ),
        Replacer.RegexReplace(
            """\s*del (.+?);""",
            """def \1;""",
            replacement = ""
        ),
        Replacer.RegexReplace(
            """\s*push (.+?);""",
            """pop \1;""",
            replacement = ""
        ),
        Replacer.RegexReplace(
            """(del .+?;)""",
            """(\w+ \{ nop; };)""",
            replacement = "$2\n$1"
        ),
        Replacer.RegexReplace(
            """pop (.+?);""",
            """push \1;""",
            """del \1;""",
            replacement = "del $1;"
        ),
        Replacer.RegexReplace("""\s*rot 0;""", replacement = ""),
        Replacer.Regex(
            """push (\d+?);""",
            """def (.+?);""",
            """pop \2;"""
        ) { match, code ->
            var replaced = code
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
            replaced
        },
        Replacer.Regex(
            """del (.+?);""",
            """def (.+?);""",
            """pop \2;"""
        ) { match, code ->
            val firstVar = Regex.escapeReplacement(match.groupValues[1])
            val secondVar = Regex.escape(match.groupValues[2])
            code.replace(
                """$secondVar\b""".toRegex(),
                firstVar
            )
        },
        Replacer.Regex(
            """def (.+?);""",
        ) { match, code ->
            val varName = match.groupValues[1]
            if (code.split(varName).size > 3) return@Regex code
            code.replace("""\n\s*(def|del) ${Regex.escape(varName)};""".toRegex(), "")
        },
        Replacer.ConstantCondition(true),
        Replacer.ConstantCondition(false),
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

        class RegexReplace(@Language("RegExp") vararg regex: String, val replacement: String) : Replacer {
            private val regex = regex.joinToString("""(?:\n\s*)+""").toRegex()
            override fun replace(code: String): String {
                return code.replace(regex, replacement)
            }
        }

        class Regex(
            @Language("RegExp") vararg regex: String,
            val function: (MatchResult, String) -> String
        ) : Replacer {
            private val regex = regex.joinToString("""(?:\n\s*)+""").toRegex()
            override fun replace(code: String): String {
                val match = regex.find(code)
                return if (match != null) {
                    function(match, code)
                } else {
                    code
                }
            }
        }

        data class Function(val func: (String) -> String) : Replacer {
            override fun replace(code: String): String {
                return func(code)
            }
        }

        data class ConstantCondition(val condition: Boolean) : Replacer {

            private val regex = """if ${if (condition) """(\d*[1-9]\d*)""" else "0"} \{""".toRegex()
            private val closingColon = """;\n*\s*""".toRegex()

            override fun replace(code: String): String {
                val match = regex.find(code) ?: return code
                var closingBrace = match.range.last + 1
                var braceCount = 1
                while (closingBrace < code.length) {
                    if (code[closingBrace] == '{') {
                        braceCount++
                    } else if (code[closingBrace] == '}') {
                        braceCount--
                    }
                    if (braceCount == 0) {
                        break
                    }
                    closingBrace++
                }
                val end = closingColon.find(code, closingBrace)!!.range.last
                return code.replaceRange(
                    match.range.first..end,
                    if (condition) {
                        code.slice((match.range.last + 1) until closingBrace)
                    } else {
                        ""
                    }
                )
            }
        }
    }
}