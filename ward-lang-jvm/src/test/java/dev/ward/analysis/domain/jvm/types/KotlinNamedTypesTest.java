package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests that the Kotlin typer records the class of a cast target and of a catch clause. */
class KotlinNamedTypesTest {

  @Test
  void recordsTheClassOfAPlainCastTarget() {
    final Probe probe =
        Probe.kotlin("class App {\n    fun f(s: Any) {\n        val n = s as String\n    }\n}\n");

    assertThat(probe.type("String")).isEqualTo("java.lang.String");
  }

  @Test
  void recordsTheClassOfACatchClauseType() {
    final Probe probe =
        Probe.kotlin(
            "class App {\n    fun f() {\n        try {\n        } catch (e: java.io.IOException) {\n        }\n    }\n}\n");

    assertThat(probe.type("java.io.IOException")).isEqualTo("java.io.IOException");
  }
}
