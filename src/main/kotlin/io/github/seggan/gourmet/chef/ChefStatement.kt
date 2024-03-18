package io.github.seggan.gourmet.chef

/*
Take ingredient from refrigerator.
This reads a numeric value from STDIN into the ingredient named, overwriting any previous value.
Put ingredient into [nth] mixing bowl.
This puts the ingredient into the nth mixing bowl.
Fold ingredient into [nth] mixing bowl.
This removes the top value from the nth mixing bowl and places it in the ingredient.
Add ingredient [to [nth] mixing bowl].
This adds the value of ingredient to the value of the ingredient on top of the nth mixing bowl and stores the result in the nth mixing bowl.
Remove ingredient [from [nth] mixing bowl].
This subtracts the value of ingredient from the value of the ingredient on top of the nth mixing bowl and stores the result in the nth mixing bowl.
Combine ingredient [into [nth] mixing bowl].
This multiplies the value of ingredient by the value of the ingredient on top of the nth mixing bowl and stores the result in the nth mixing bowl.
Divide ingredient [into [nth] mixing bowl].
This divides the value of ingredient into the value of the ingredient on top of the nth mixing bowl and stores the result in the nth mixing bowl.
Liquefy | Liquify contents of the [nth] mixing bowl.
This turns all the ingredients in the nth mixing bowl into a liquid, i.e. a Unicode characters for output purposes.
Stir [the [nth] mixing bowl] for number minutes.
This "rolls" the top number ingredients in the nth mixing bowl, such that the top ingredient goes down that number of ingredients and all ingredients above it rise one place. If there are not that many ingredients in the bowl, the top ingredient goes to tbe bottom of the bowl and all the others rise one place.
Stir ingredient into the [nth] mixing bowl.
This rolls the number of ingredients in the nth mixing bowl equal to the value of ingredient, such that the top ingredient goes down that number of ingredients and all ingredients above it rise one place. If there are not that many ingredients in the bowl, the top ingredient goes to the bottom of the bowl and all the others rise one place.
Mix [the [nth] mixing bowl] well.
This randomises the order of the ingredients in the nth mixing bowl.
Clean [nth] mixing bowl.
This removes all the ingredients from the nth mixing bowl.
Pour contents of the [nth] mixing bowl into the [pth] baking dish.
This copies all the ingredients from the nth mixing bowl to the pth baking dish, retaining the order and putting them on top of anything already in the baking dish.
Verb the ingredient.
This marks the beginning of a loop. It must appear as a matched pair with the following statement. The loop executes as follows: The value of ingredient is checked. If it is non-zero, the body of the loop executes until it reaches the "until" statement. The value of ingredient is rechecked. If it is non-zero, the loop executes again. If at any check the value of ingredient is zero, the loop exits and execution continues at the statement after the "until". Loops may be nested.
Verb [the ingredient] until verbed.
This marks the end of a loop. It must appear as a matched pair with the above statement. verbed must match the Verb in the matching loop start statement. The Verb in this statement may be arbitrary and is ignored. If the ingredient appears in this statement, its value is decremented by 1 when this statement executes. The ingredient does not have to match the ingredient in the matching loop start statement.
Set aside.
This causes execution of the innermost loop in which it occurs to end immediately and execution to continue at the statement after the "until".
Serve with auxiliary-recipe.
This invokes a sous-chef to immediately prepare the named auxiliary-recipe. The calling chef waits until the sous-chef is finished before continuing. See the section on auxiliary recipes below.
Refrigerate [for number hours].
This causes execution of the recipe in which it appears to end immediately. If in an auxiliary recipe, the auxiliary recipe ends and the sous-chef's first mixing bowl is passed back to the calling chef as normal. If a number of hours is specified, the recipe will print out its first number baking dishes (see the Serves statement below) before ending.
 */
sealed class ChefStatement(val toCode: ChefStatement.() -> String) {

    data class ReadNum(val reg: String) : ChefStatement({ "Take $reg from refrigerator." })

    data class Push(val reg: String, val stack: ChefStack) : ChefStatement({ "Put $reg into $stack." })

    data class Pop(val reg: String, val stack: ChefStack) : ChefStatement({ "Fold $reg into $stack." })

    data class Add(val reg: String, val stack: ChefStack) : ChefStatement({ "Add $reg to $stack." })

    data class Sub(val reg: String, val stack: ChefStack) : ChefStatement({ "Remove $reg from $stack." })

    data class Mul(val reg: String, val stack: ChefStack) : ChefStatement({ "Combine $reg into $stack." })

    data class Div(val reg: String, val stack: ChefStack) : ChefStatement({ "Divide $reg into $stack." })

    data class Liquefy(val stack: ChefStack) : ChefStatement({ "Liquify contents of the $stack." })

    data class Rotate(val stack: ChefStack, val num: Int) : ChefStatement({ "Stir the $stack for $num minutes." })

    data class RotateByReg(val stack: ChefStack, val reg: String) : ChefStatement({ "Stir $reg into the $stack." })

    data class Randomize(val stack: ChefStack) : ChefStatement({ "Mix the $stack well." })

    data class Clear(val stack: ChefStack) : ChefStatement({ "Clean $stack." })

    data class Output(val stack: ChefStack) : ChefStatement({ "Pour contents of the $stack into the baking dish." })

    data class StartLoop(val reg: String) : ChefStatement({ "V the $reg." })

    data class EndLoop(val reg: String?) : ChefStatement({
        if (reg != null) "V the $reg until ved."
        else "V until ved."
    })

    data object Break : ChefStatement({ "Set aside." })

    data class Call(val recipe: String) : ChefStatement({ "Serve with $recipe." })

    data object Return : ChefStatement({ "Refrigerate." })
}