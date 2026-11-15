package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinTaintFixtures.count;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The control flow inside and around a lambda. */
class KotlinLambdaControlFlowTest {

  @Test
  void aBranchInsideALambdaKeepsTheCleanPathApart() {
    assertThat(
            count(
                "val q = src()\nq.let {\n var s = it\n if (it.isEmpty()) { s = \"x\" }\n sink(s)\n }"))
        .isOne();
    assertThat(
            count(
                "val q = src()\nq.let {\n var s = it\n if (it.isEmpty()) { s = \"x\"\n sink(s) }\n }"))
        .isZero();
  }

  @Test
  void aLabelledReturnLeavesTheLambdaWithItsValue() {
    assertThat(count("val n = src().let { if (it.isEmpty()) return@let \"x\"\n it }\nsink(n)"))
        .isOne();
    assertThat(count("val n = src().let { return@let clean(it) }\nsink(n)")).isZero();
  }

  @Test
  void aLambdaThatMayNotRunLeavesTheEarlierValueReachable() {
    assertThat(count("var s = src()\nlistOf(a).forEach { s = \"x\" }\nsink(s)")).isOne();
  }

  @Test
  void aChainOfScopeFunctionsPassesTheValueAlong() {
    assertThat(count("val n = src().let { it + a }.also { sink(it) }")).isOne();
    assertThat(count("val n = src().let { clean(it) }.also { sink(it) }")).isZero();
  }
}
