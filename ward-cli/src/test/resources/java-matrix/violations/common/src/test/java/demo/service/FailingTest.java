package demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FailingTest {
  @Test
  void fails() {
    assertEquals(1, 2);
  }
}
