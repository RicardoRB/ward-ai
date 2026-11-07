package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KotlinTypeFactsTest {
  private static final String SOURCE =
      "package app\nimport java.util.Objects\nclass User(val name: String, var age: Int = 3) {\n"
          + "  val tag: String = \"t\"\n  fun run(items: List<String>): Boolean {\n"
          + "    val v = name.length\n    val s = Objects.equals(name, \"x\")\n    val u = User(\"a\")\n"
          + "    val w = u.name\n    val first = items.get(0)\n    val sum = v + 1\n    val text = \"a\" + v\n"
          + "    for (q in items) { val z = q.trim() }\n    return s\n  }\n}\n";

  @Test
  void typesLiteralsPropertiesAndLibraryCalls() {
    final Probe probe = Probe.kotlin(SOURCE);

    assertThat(probe.type("name.length")).isEqualTo("int");
    assertThat(probe.type("Objects.equals(name, \"x\")")).isEqualTo("boolean");
    assertThat(probe.call("Objects.equals(name, \"x\")").orElseThrow().declaringType().name())
        .isEqualTo("java.util.Objects");
    assertThat(probe.type("User(\"a\")")).isEqualTo("app.User");
    assertThat(probe.type("u.name")).isEqualTo("java.lang.String");
    assertThat(probe.type("items.get(0)")).isEqualTo("java.lang.String");
    assertThat(probe.type("v + 1")).isEqualTo("int");
    assertThat(probe.type("\"a\" + v")).isEqualTo("java.lang.String");
    assertThat(probe.type("q.trim()")).isEqualTo("java.lang.String");
  }

  @Test
  void leavesWhatNeedsFullInferenceUnknown() {
    final Probe probe =
        Probe.kotlin(
            "package app\nfun f(items: List<String>) {\n  val a = items.map { it.length }\n  val b = missing.value\n}\n");

    assertThat(probe.type("items.map { it.length }")).isEqualTo("?");
    assertThat(probe.type("missing.value")).isEqualTo("?");
  }
}
