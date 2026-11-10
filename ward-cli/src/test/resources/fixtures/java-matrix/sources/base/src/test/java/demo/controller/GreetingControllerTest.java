package demo.controller;

import demo.repository.GreetingRepository;
import demo.service.Greeter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GreetingControllerTest {
    @Test
    void trimsTheNameBeforeGreeting() {
        final GreetingController controller =
                new GreetingController(new Greeter(new GreetingRepository("Hello, ")));

        Assertions.assertEquals("Hello, Ada", controller.handle("  Ada "), "trimmed greeting");
    }
}
