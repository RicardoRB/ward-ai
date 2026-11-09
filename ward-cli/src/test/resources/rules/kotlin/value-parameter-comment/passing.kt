package fixture

fun greet(
    // The name, on its own line above the parameter.
    name: String,
    /** Documented parameter. */
    punctuation: String,
) = name + punctuation
