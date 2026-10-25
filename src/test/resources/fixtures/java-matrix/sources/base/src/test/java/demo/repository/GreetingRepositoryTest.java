package demo.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreetingRepositoryTest {
  @Test
  void returnsThePrefix() {
    assertEquals("Hi, ", new GreetingRepository("Hi, ").prefix());
  }
}
