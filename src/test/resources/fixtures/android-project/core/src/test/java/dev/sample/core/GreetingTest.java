package dev.sample.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GreetingTest {
  @Test
  public void greetsByName() {
    assertEquals("Hello, Ada", Greeting.of("Ada"));
  }
}
