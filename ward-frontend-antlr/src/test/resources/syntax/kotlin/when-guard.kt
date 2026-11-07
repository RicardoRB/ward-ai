package example

fun describe(value: Any): String =
    when (value) {
        is String if value.isEmpty() -> "empty"
        is String -> "text"
        else -> "other"
    }
