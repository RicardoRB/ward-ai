import Observation
import Testing

@Observable final class Counter {
  var count = 0
}

@Suite struct ArithmeticTests {
  @Test("adds numbers") func addition() {
    #expect(1 + 1 == 2)
  }
}
