package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnusedClassLiteralCheckTest {

  private static List<String> run(final String body) {
    return new ParsedFacts("final class C {\n  Object t() {\n" + body + "  }\n}\n")
        .run(new UnusedClassLiteralCheck("message"));
  }

  @Test
  void aVariableSetToAClassLiteralAndNeverUsedIsReported() {
    assertThat(run("    Class<?> type = String.class;\n    return null;\n"))
        .containsExactly("3:14");
    assertThat(run("    Class<?> type = java.lang.String.class;\n    return null;\n")).hasSize(1);
  }

  @Test
  void aUsedVariableOrAnotherInitializerIsNotReported() {
    assertThat(run("    Class<?> type = String.class;\n    return type;\n")).isEmpty();
    assertThat(run("    Object type = new Object();\n    return null;\n")).isEmpty();
    assertThat(run("    Object type = String.classLoader;\n    return null;\n")).isEmpty();
  }
}
