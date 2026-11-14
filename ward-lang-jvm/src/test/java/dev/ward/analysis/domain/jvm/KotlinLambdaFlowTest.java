package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinTaintFixtures.count;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A lambda is lowered into the graph of its body, with its receiver before it. */
class KotlinLambdaFlowTest {

  @Test
  void aSanitizerAsTheLastExpressionOfLetCleansTheResult() {
    assertThat(count("val n = src().let { clean(it) }\nsink(n)")).isZero();
  }

  @Test
  void theSameLambdaWithoutTheSanitizerKeepsTheTaint() {
    assertThat(count("val n = src().let { it.trim() }\nsink(n)")).isOne();
  }

  @Test
  void theParameterItCarriesTheTaintOfTheReceiverIntoTheLambda() {
    assertThat(count("val q = src()\nq.let { sink(it) }")).isOne();
  }

  @Test
  void aNamedParameterCarriesTheTaintToo() {
    assertThat(count("val q = src()\nq.let { value -> sink(value) }")).isOne();
  }

  @Test
  void aCleanReceiverGivesACleanParameter() {
    assertThat(count("val q = a\nq.let { sink(it) }")).isZero();
  }

  @Test
  void aSourceWrittenInTheReceiverReachesTheParameter() {
    assertThat(count("src().let { sink(it) }")).isOne();
  }

  @Test
  void alsoGivesBackTheReceiverNotTheLambdaValue() {
    assertThat(count("val n = src().also { clean(it) }\nsink(n)")).isOne();
    assertThat(count("val n = a.also { sink(clean(src())) }\nsink(n)")).isZero();
  }

  @Test
  void aValueThatTheLambdaDoesNotUseIsNotTheCallsValue() {
    assertThat(count("val n = a.let { src() }\nsink(n)")).isOne();
  }

  @Test
  void aStatementAroundALambdaStillReadsItsOwnNames() {
    assertThat(count("val p = src()\nval n = p + a.let { it }\nsink(n)")).isOne();
  }
}
