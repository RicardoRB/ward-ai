package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import org.junit.jupiter.api.Test;

/** Taint through a value that a logical or conditional operator picks. */
class NodeSplitValueTaintTest {
  private static int found(final String body) {
    return NodeTaintFixtures.runFile(
        AnalysisLanguage.TYPESCRIPT,
        "function use(req) {\n" + body + "  sink(v);\n}\n"
    ).size();
  }

  /** Scenario: an operand that is untrusted makes the picked value untrusted. */
  @Test
  void anUntrustedOperandTaintsTheValue() {
    assertThat(found("  const v = req.query || 'd';\n")).isEqualTo(1);
    assertThat(found("  const v = 'd' && req.query;\n")).isEqualTo(1);
    assertThat(found("  const v = true ? req.query : 'd';\n")).isEqualTo(1);
    assertThat(found("  const v = true ? 'd' : req.query;\n")).isEqualTo(1);
    assertThat(found("  let v = 'x';\n  v = req.query ?? v;\n")).isEqualTo(1);
  }

  /** Scenario: an untrusted condition only picks between constants, so the value is constant. */
  @Test
  void anUntrustedConditionDoesNotTaintTheValue() {
    assertThat(found("  const v = req.query ? 'a' : 'b';\n")).isZero();
  }

  /** Scenario: an allow-list test on the value gives one of the listed constants. */
  @Test
  void anAllowListTernaryGivesAConstant() {
    assertThat(
        found("  const k = req.query;\n  const v = ['a', 'b'].includes(k) ? k : 'a';\n")
    ).isZero();
    assertThat(
        found("  const k = req.query;\n  const v = ['a', req.query].includes(k) ? k : 'a';\n")
    ).isEqualTo(1);
    assertThat(
        found("  const k = req.query;\n  const v = ['a', 'b'].includes(k) ? req.query : 'a';\n")
    ).isEqualTo(1);
  }
}
