package io.github.seggan.gourmet.chef

data class ChefProgram(
    val name: String,
    val ingredients: List<Pair<Int, String>>,
    val steps: List<ChefStatement>,
    val functions: List<ChefProgram>
) {

    fun toCode(): String {
        val ingredients = ingredients.joinToString("\n") { (amount, name) ->
            "$amount $name"
        }
        val steps = steps.joinToString(" ") { it.toCode(it) }
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