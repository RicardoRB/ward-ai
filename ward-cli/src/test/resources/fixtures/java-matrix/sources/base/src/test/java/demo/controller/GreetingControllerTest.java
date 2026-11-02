package demo.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import demo.repository.GreetingRepository;
import demo.service.Greeter;
import org.junit.jupiter.api.Test;

class GreetingControllerTest {
  @Test
  void trimsTheNameBeforeGreeting() {
    final GreetingController controller =
        new GreetingController(new Greeter(new GreetingRepository("Hello, ")));

    assertEquals("Hello, Ada", controller.handle("  Ada "));
  }
}
