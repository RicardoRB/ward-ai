import org.junit.jupiter.api.Test;

final class CounterTest {
  @Test
  void countsUp() {
    assertEquals(2, 1 + 1);
  }

  @Test
  void checksThroughAHelper() {
    checkTotals(2);
  }

  @Test
  void verifiesThroughAHelper() {
    verifyTotals(2);
  }
}
