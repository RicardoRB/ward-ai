package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * An option of each generic check kind, changed in {@code ward-rules.toml}, changes the finding.
 */
class GenericRuleOptionsGateTest {

  private static final String SOURCE =
      """
      import a.B;
      class A {
        static final int maxSize = 3;
        static public int bad = 1;
        int one(int x) {
          if (x > 0) { if (x > 1) { if (x > 2) { return x > 5 ? 1 : 0; } } }
          return 7;
        }
        int two() { return 0; }
        int three() { return 1; }
        // TODO: later
      }
      """;

  @TempDir Path dir;

  static Stream<Arguments> options() {
    return Stream.of(
        Arguments.of("constant-name", "pattern = \"[a-zA-Z]+\""),
        Arguments.of("inline-conditional", "text = \"zzz\""),
        Arguments.of("method-count", "max = 5"),
        Arguments.of("nested-if", "max = 3"),
        Arguments.of("todo-comment", "pattern = \"FIXME:\""),
        Arguments.of("modifier-order", "order = [\"static\", \"public\"]"),
        Arguments.of("magic-number", "ignore = [\"0\", \"1\", \"2\", \"5\", \"7\"]"));
  }

  @ParameterizedTest
  @MethodSource("options")
  void anOptionSetInWardRulesTomlChangesTheFinding(final String rule, final String option)
      throws IOException {
    final String id = "ward:java/example-" + rule;
    final FixtureProject project =
        new FixtureProject(dir, new ClasspathRuleSource(List.of("rules-examples/java.toml")));
    Files.createDirectories(dir.resolve("src"));
    Files.writeString(dir.resolve(FixtureProject.SRC_A), SOURCE);

    project.config(FixtureProject.VERSION);
    assertThat(FixtureProject.ids(project.run(List.of(), null, new HashMap<>()))).contains(id);

    project.config(FixtureProject.VERSION + "[rules.java.example-" + rule + "]\n" + option + "\n");
    assertThat(FixtureProject.ids(project.run(List.of(), null, new HashMap<>())))
        .doesNotContain(id);
  }
}
