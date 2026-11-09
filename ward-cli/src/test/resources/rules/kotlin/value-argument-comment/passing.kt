package fixture

fun greet(name: String, punctuation: String) = name + punctuation

val text = greet(
    // The name, on its own line above the argument.
    "Ward",
    punctuation = "!",
)
