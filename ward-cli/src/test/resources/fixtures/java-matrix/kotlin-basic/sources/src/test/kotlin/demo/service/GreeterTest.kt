package demo.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Tests for [Greeter]. */
class GreeterTest {
    @Test
    fun greetsByName() {
        assertEquals("Hello, Ada", Greeter("Hello").greet("Ada"))
    }
}
