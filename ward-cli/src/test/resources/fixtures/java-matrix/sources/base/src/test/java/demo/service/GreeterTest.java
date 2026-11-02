package demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import demo.repository.GreetingRepository;
import org.junit.jupiter.api.Test;

class GreeterTest {
  private final Greeter greeter = new Greeter(new GreetingRepository("Hello, "));

  @Test
  void greetsByName() {
    assertEquals("Hello, Ada", greeter.greet("Ada"));
  }

  @Test
  void greetsWorldWhenNameIsNull() {
    assertEquals("Hello, world", greeter.greet(null));
  }

  @Test
  void greetsWorldWhenNameIsEmpty() {
    assertEquals("Hello, world", greeter.greet(""));
  }
}
