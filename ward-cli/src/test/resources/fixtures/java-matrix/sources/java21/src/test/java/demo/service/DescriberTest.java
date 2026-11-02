package demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DescriberTest {
  private final Describer describer = new Describer();

  @Test
  void describesPositiveNumbers() {
    assertEquals("positive", describer.describe(1));
  }

  @Test
  void describesZeroAsNotPositive() {
    assertEquals("not positive", describer.describe(0));
  }

  @Test
  void describesText() {
    assertEquals("text hi", describer.describe("hi"));
  }

  @Test
  void describesAnythingElseAsUnknown() {
    assertEquals("unknown", describer.describe(1.5));
  }
}
