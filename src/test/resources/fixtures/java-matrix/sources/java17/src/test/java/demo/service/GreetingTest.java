package demo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GreetingTest {
  @Test
  void personalWhenTheTextNamesThePerson() {
    assertTrue(new Greeting("Ada", "Hello, Ada").personal());
  }

  @Test
  void notPersonalOtherwise() {
    assertFalse(new Greeting("Ada", "Hello, world").personal());
  }
}
