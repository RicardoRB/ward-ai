package fixture

fun greet(name: String, punctuation: String) = name + punctuation

val text = greet(
    name = /* the name */ "Ward",
    punctuation = "!",
)
