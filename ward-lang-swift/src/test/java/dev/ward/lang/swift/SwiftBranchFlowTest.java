package dev.ward.lang.swift;

import static dev.ward.lang.swift.SwiftFlowFixtures.count;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Statements and branches of the Swift lowering, read through the taint analysis. */
final class SwiftBranchFlowTest {
  @Test
  void aValueFromASourceReachesASink() {
    assertThat(count("let x = src()\nsink(x)")).isEqualTo(1);
  }

  @Test
  void aSanitizerCleansItsArgument() {
    assertThat(count("let x = src()\nsink(clean(x))")).isZero();
  }

  @Test
  void aSanitizingMemberCleansItsReceiver() {
    assertThat(count("let x = src().safe\nsink(x)")).isZero();
  }

  @Test
  void aWriteThatReplacesTheValueEndsTheFlow() {
    assertThat(count("var x = src()\nx = \"fixed\"\nsink(x)")).isZero();
  }

  @Test
  void aValueFromOneBranchOfAnIfReaches() {
    assertThat(count("var x = \"\"\nif a.isEmpty { x = src() } else { x = \"k\" }\nsink(x)"))
        .isEqualTo(1);
  }

  @Test
  void aValueFromAnElseIfBranchReaches() {
    assertThat(
        count("var x = \"\"\nif a.isEmpty { x = \"1\" } else if a == \"b\" { x = src() }\nsink(x)")
    )
        .isEqualTo(1);
  }

  @Test
  void aGuardBindingKeepsTheTaintOfItsInitializer() {
    assertThat(count("guard let v = Optional(src()) else { return }\nsink(v)")).isEqualTo(1);
  }

  @Test
  void aGuardWithSeveralConditionsBindsInOrder() {
    assertThat(count("guard !a.isEmpty, let v = Optional(src()) else { return }\nsink(v)"))
        .isEqualTo(1);
  }

  @Test
  void aValueWrittenInAWhileLoopReachesAfterIt() {
    assertThat(count("var x = \"\"\nwhile x.isEmpty { x = src() }\nsink(x)")).isEqualTo(1);
  }
}
