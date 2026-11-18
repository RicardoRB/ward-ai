package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Kotlin taint follows {@code this} and implicit-receiver calls inside receiver lambdas. */
class KotlinScopeFunctionTaintTest {
  @Test
  @DisplayName("Scenario: Kotlin scope functions")
  void kotlinScopeFunctions() {
    // Given x.run { exec(this) } with a tainted x
    final String body = "val x = src()\nx.run { sink(this) }";

    // When the Kotlin taint rules run
    final int found = KotlinTaintFixtures.count(body);

    // Then the call is reported
    assertThat(found).isEqualTo(1);
  }

  @Test
  void withAndApplyKeepTheReceiverAsThis() {
    assertThat(KotlinTaintFixtures.count("with(src()) { sink(this) }")).isEqualTo(1);
    assertThat(KotlinTaintFixtures.count("src().apply { sink(this) }")).isEqualTo(1);
  }

  @Test
  void anUnqualifiedCallInsideTheLambdaUsesTheImplicitReceiver() {
    assertThat(KotlinTaintFixtures.count("src().run { sink(trim()) }")).isEqualTo(1);
    assertThat(KotlinTaintFixtures.count("with(src()) { sink(trim()) }")).isEqualTo(1);
  }

  @Test
  void aSanitisedThisIsNotReported() {
    assertThat(KotlinTaintFixtures.count("src().run { sink(clean(this)) }")).isZero();
    assertThat(KotlinTaintFixtures.count("with(src()) { sink(clean(trim())) }")).isZero();
  }

  @Test
  void aCleanReceiverIsNotReported() {
    assertThat(KotlinTaintFixtures.count("\"fixed\".run { sink(this) }")).isZero();
    assertThat(KotlinTaintFixtures.count("\"fixed\".run { sink(trim()) }")).isZero();
  }
}
