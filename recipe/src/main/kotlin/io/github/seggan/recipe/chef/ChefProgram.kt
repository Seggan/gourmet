package io.github.seggan.recipe.chef

import java.math.BigInteger

data class ChefProgram(
    val name: String,
    val ingredients: List<Pair<BigInteger?, String>>,
    val steps: List<ChefStatement>,
    val functions: List<ChefProgram>
) {

    fun toCode(): String {
        val ingredients = ingredients.joinToString("\n") { (amount, name) ->
            if (amount == null) name else "$amount $name"
        }
        val steps = steps.joinToString("\n") { it.toCode(it) }
        val functions = functions.joinToString("\n\n") { it.toCode() }
        return """
            $name.
            
            Ingredients.
            %s
            
            Method.
            %s
            
            Serves 1.
            
            %s
        """.trimIndent().format(ingredients, steps, functions)
    }
}