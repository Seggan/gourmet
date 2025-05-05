package io.github.seggan.recipe.chef

import java.math.BigInteger

data class ChefProgram(
    val name: String,
    val ingredients: List<Pair<BigInteger?, String>>,
    val steps: List<ChefStatement>,
    val functions: List<ChefProgram>,
    val comment: String = "",
) {

    fun toCode(small: Boolean): String {
        val ingredients = ingredients.joinToString("\n") { (amount, name) ->
            if (amount == null) name else "$amount $name"
        }
        val steps = steps.joinToString(if (small) "" else "\n") { it.toCode(it) }
        val functions = functions.joinToString("\n\n") { it.toCode(small) }
        return """
            ${if (small) "a" else name}.%s
            
            Ingredients.
            %s
            
            Method.
            %s
            
            Serves 1.
            
            %s
        """.trimIndent().format(if (small) "" else "\n\n$comment", ingredients, steps, functions).trim()
    }
}