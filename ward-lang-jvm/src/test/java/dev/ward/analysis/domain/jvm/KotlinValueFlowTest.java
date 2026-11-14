package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinTaintFixtures.count;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * An {@code if}, {@code when} or {@code try} that makes a value, and the short-circuit operators.
 */
class KotlinValueFlowTest {

  @Test
  void anIfThatMakesAValueTaintsItWhenOneBranchIsTainted() {
    assertThat(count("val v = if (a.isEmpty()) src() else a\nsink(v)")).isOne();
    assertThat(count("val v = if (a.isEmpty()) a else src()\nsink(v)")).isOne();
  }

  @Test
  void anIfThatMakesAValueIsCleanWhenEveryBranchIsClean() {
    assertThat(count("val v = if (a.isEmpty()) clean(src()) else a\nsink(v)")).isZero();
  }

  @Test
  void anIfValueInsideAnArgumentIsReadWhereItIsWritten() {
    assertThat(count("sink(if (a.isEmpty()) src() else \"x\")")).isOne();
    assertThat(count("sink(if (a.isEmpty()) clean(src()) else \"x\")")).isZero();
  }

  @Test
  void aWhenThatMakesAValueJoinsItsEntries() {
    assertThat(count("val v = when (a) { \"x\" -> a\n else -> src() }\nsink(v)")).isOne();
    assertThat(count("val v = when (a) { \"x\" -> clean(src())\n else -> a }\nsink(v)")).isZero();
  }

  @Test
  void aTryThatMakesAValueJoinsTheBodyAndTheHandlers() {
    assertThat(count("val v = try { src() } catch (e: Exception) { a }\nsink(v)")).isOne();
    assertThat(count("val v = try { clean(src()) } catch (e: Exception) { \"x\" }\nsink(v)"))
        .isZero();
  }

  @Test
  void theLastExpressionOfALambdaCanBeAnIf() {
    assertThat(count("val n = src().let { if (it.isEmpty()) \"x\" else clean(it) }\nsink(n)"))
        .isZero();
    assertThat(count("val n = src().let { if (it.isEmpty()) \"x\" else it }\nsink(n)")).isOne();
  }

  @Test
  void anElvisTakesTheTaintOfEitherSide() {
    assertThat(count("val v = a ?: src()\nsink(v)")).isOne();
    assertThat(count("val v = src() ?: a\nsink(v)")).isOne();
    assertThat(count("val v = clean(src()) ?: a\nsink(v)")).isZero();
  }

  @Test
  void anElvisThatReturnsLeavesOnlyTheNonNullPathToTheCode() {
    assertThat(count("var s = \"\"\nval v = a ?: run { s = src()\n return }\nsink(s)")).isZero();
  }

  @Test
  void theRightOperandOfAnAndMayNotRun() {
    assertThat(count("var s = src()\nval ok = a.isEmpty() && run { s = \"x\"\n true }\nsink(s)"))
        .isOne();
    assertThat(count("var s = src()\nval ok = a.isEmpty() || run { s = \"x\"\n true }\nsink(s)"))
        .isOne();
  }
}
