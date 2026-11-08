package demo.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GreeterTest {
    @Test
    fun greetsByName() {
        assertEquals("Hello, Ada", Greeter("Hello").greet("Ada"))
    }
}
