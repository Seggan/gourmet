package io.github.seggan.gourmet.compilation

object PeepholeOptimizer {

    private val patterns: List<Pair<Regex, String>>

    init {
        val protoPatterns = listOf(
            listOf(
                """push (.+?);""",
                """(\w+) \{ nop; \};"""
            ) to """$2 $1;"""
        )

        patterns = protoPatterns.map { (patterns, replacement) ->
            patterns.joinToString("""\n\s*""").toRegex() to replacement
        }
    }

    fun optimize(code: String): String {
        var optimized = code
        do {
            val old = optimized
            for ((pattern, replacement) in patterns) {
                optimized = pattern.replace(optimized, replacement)
            }
        } while (old != optimized)
        return optimized
    }
}