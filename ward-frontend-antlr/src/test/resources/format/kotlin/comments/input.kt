/**
 * Adds two numbers.
 *   Keeps indentation of KDoc lines.
 */
fun add(a: Int, b: Int): Int {
    // own-line comment before the return
    val sum = a + b // trailing comment
    /* block comment */ return sum
}

// a comment before a declaration
val flag = true   /* trailing block */
