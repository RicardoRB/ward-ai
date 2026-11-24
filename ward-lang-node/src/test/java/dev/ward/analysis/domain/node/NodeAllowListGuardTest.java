package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import org.junit.jupiter.api.Test;

/** The allow-list checks of a Node body, in its own flow and for the callers of a function. */
class NodeAllowListGuardTest {
  private static final String LIST = "const L = ['a', 'b'];\n";
  private static final String CHECK =
      "function check(value) {\n  if (!L.includes(value)) {\n    throw new Error('x');\n  }\n}\n";

  private static int found(final String source) {
    return NodeTaintFixtures.runFile(AnalysisLanguage.TYPESCRIPT, source).size();
  }

  /** Scenario: a function that throws unless its parameter is on the list vouches for callers. */
  @Test
  void aCalleeThatThrowsUnlessTheValueIsListedCleansTheArgument() {
    assertThat(
        found(
            LIST + CHECK + "function use(req) {\n  const k = req.query;\n  check(k);\n  sink(k);\n}\n"
        )
    ).isZero();
  }

  /** Scenario: a callee that only returns early on a bad value leaves the argument tainted. */
  @Test
  void aCalleeThatReturnsEarlyLeavesTheArgumentTainted() {
    final String early =
        "function check(value) {\n  if (!L.includes(value)) {\n    return;\n  }\n}\n";
    assertThat(
        found(
            LIST + early + "function use(req) {\n  const k = req.query;\n  check(k);\n  sink(k);\n}\n"
        )
    ).isEqualTo(1);
  }

  /** Scenario: a value read after the call into the argument position stays tainted. */
  @Test
  void aValueThatIsNotTheCheckedArgumentStaysTainted() {
    assertThat(
        found(
            LIST + CHECK
                + "function use(req) {\n  const k = req.query;\n  const o = req.query;\n"
                + "  check(k);\n  sink(o);\n}\n"
        )
    ).isEqualTo(1);
  }
}
