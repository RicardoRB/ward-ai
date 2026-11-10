package demo.service;

import demo.repository.GreetingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GreeterTest {
    private static final String PREFIX = "Hello, ";
    private static final String HELLO_WORLD = "Hello, world";
    private final Greeter greeter = new Greeter(new GreetingRepository(PREFIX));

    @Test
    void greetsByName() {
        Assertions.assertEquals("Hello, Ada", greeter.greet("Ada"), "greeting by name");
    }

    @Test
    void greetsWorldWhenNameIsNull() {
        Assertions.assertEquals(HELLO_WORLD, greeter.greet(null), "greeting for null");
    }

    @Test
    void greetsWorldWhenNameIsEmpty() {
        Assertions.assertEquals(HELLO_WORLD, greeter.greet(""), "greeting for empty");
    }
}
