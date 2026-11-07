package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in parity check: runs real Checkstyle 14.3.0 on the Java coding fixtures and compares each
 * violating fixture's positions with the expected ones. Skipped unless {@code
 * -Dward.checkstyle.classpath} names the jars of Checkstyle 14.3.0 and its dependencies.
 */
class CodingCheckstyleParityTest {

  private static final String CLASSPATH = System.getProperty("ward.checkstyle.classpath");
  private static final Pattern FINDING = Pattern.compile(":(\\d+):(\\d+): ");
  private static final String TRUE = "true";

  /**
   * Cases whose Checkstyle positions differ from Ward's, with the reason. The lines agree in each;
   * only the columns differ: Ward reports the whole expression that Checkstyle reports the operator
   * of.
   */
  private static final Map<String, String> KNOWN_DIFFERENCES =
      Map.of(
          "string-literal-equality",
          "Checkstyle reports the == or != operator; Ward reports the comparison",
          "avoid-inline-conditionals",
          "Checkstyle reports the ? operator; Ward reports the conditional expression");

  private static final List<Mirror> MIRRORS =
      List.of(
          new Mirror("empty-statement", "", "EmptyStatement", Map.of()),
          new Mirror("equals-hash-code", "", "EqualsHashCode", Map.of()),
          new Mirror("fall-through", "", "FallThrough", Map.of()),
          new Mirror(
              "fall-through", "check-last", "FallThrough", Map.of("checkLastCaseGroup", TRUE)),
          new Mirror("illegal-catch", "", "IllegalCatch", Map.of()),
          new Mirror(
              "illegal-catch",
              "custom",
              "IllegalCatch",
              Map.of("illegalClassNames", "IllegalStateException")),
          new Mirror("illegal-throws", "", "IllegalThrows", Map.of()),
          new Mirror(
              "illegal-throws",
              "override",
              "IllegalThrows",
              Map.of("ignoreOverriddenMethods", "false")),
          new Mirror("missing-switch-default", "", "MissingSwitchDefault", Map.of()),
          new Mirror("string-literal-equality", "", "StringLiteralEquality", Map.of()),
          new Mirror("avoid-inline-conditionals", "", "AvoidInlineConditionals", Map.of()),
          new Mirror("simplify-boolean-return", "", "SimplifyBooleanReturn", Map.of()),
          new Mirror("covariant-equals", "", "CovariantEquals", Map.of()),
          new Mirror("equals-avoid-null", "", "EqualsAvoidNull", Map.of()),
          new Mirror(
              "equals-avoid-null",
              "ignore-case",
              "EqualsAvoidNull",
              Map.of("ignoreEqualsIgnoreCase", TRUE)),
          new Mirror("return-count", "", "ReturnCount", Map.of()),
          new Mirror("return-count", "void", "ReturnCount", Map.of("maxForVoid", "2")),
          new Mirror("nested-if-depth", "", "NestedIfDepth", Map.of()),
          new Mirror("nested-for-depth", "", "NestedForDepth", Map.of()),
          new Mirror("nested-try-depth", "", "NestedTryDepth", Map.of()));

  @Test
  void checkstyleReportsTheSamePositionsAsEveryCodingFixture() throws Exception {
    Assumptions.assumeTrue(CLASSPATH != null, "set -Dward.checkstyle.classpath to run");
    final List<String> problems = new ArrayList<>();
    for (final Mirror mirror : MIRRORS) {
      final String key = mirror.rule() + (mirror.fixture().isEmpty() ? "" : "-" + mirror.fixture());
      final Path directory = RuleFixtureSet.root().resolve("java").resolve(mirror.rule());
      final String suffix = mirror.fixture().isEmpty() ? "" : "-" + mirror.fixture();
      final Path config = Files.createTempFile("checkstyle-" + mirror.rule(), ".xml");
      Files.writeString(config, config(mirror), StandardCharsets.UTF_8);
      final Set<String> expected =
          expectedPositions(directory.resolve("expected" + suffix + ".txt"));
      final Set<String> found =
          positions(config, directory.resolve("violating" + suffix + ".java"));
      if (!KNOWN_DIFFERENCES.containsKey(key) && !found.equals(expected)) {
        problems.add(key + " checkstyle=" + found + " ward=" + expected);
      }
      if (suffix.isEmpty()) {
        assertThat(positions(config, directory.resolve("passing.java")))
            .as("checkstyle on passing %s", mirror.rule())
            .isEmpty();
      }
    }
    assertThat(problems).isEmpty();
  }

  private static Set<String> expectedPositions(final Path file) throws IOException {
    final Set<String> positions = new HashSet<>();
    for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      if (!line.isBlank()) {
        positions.add(line.trim());
      }
    }
    return positions;
  }

  private static String config(final Mirror mirror) {
    final StringBuilder properties = new StringBuilder();
    mirror
        .properties()
        .forEach(
            (key, value) ->
                properties.append("<property name=\"%s\" value=\"%s\"/>".formatted(key, value)));
    return "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE module PUBLIC \"-//Checkstyle//DTD Checkstyle Configuration 1.3//EN\""
        + " \"https://checkstyle.org/dtds/configuration_1_3.dtd\">"
        + "<module name=\"Checker\"><module name=\"TreeWalker\"><module name=\""
        + mirror.module()
        + "\">"
        + properties
        + "</module></module></module>";
  }

  private static Set<String> positions(final Path config, final Path file)
      throws IOException, InterruptedException {
    final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    final Process process =
        new ProcessBuilder(
                java,
                "-cp",
                CLASSPATH,
                "com.puppycrawl.tools.checkstyle.Main",
                "-c",
                config.toString(),
                file.toString())
            .redirectErrorStream(true)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    final Set<String> found = new HashSet<>();
    for (final String line : output.split("\n")) {
      final Matcher matcher = FINDING.matcher(line);
      if (matcher.find()) {
        found.add(matcher.group(1) + ":" + matcher.group(2));
      }
    }
    return found;
  }

  /** One Ward rule case and the Checkstyle module that mirrors it, with the module's properties. */
  record Mirror(String rule, String fixture, String module, Map<String, String> properties) {}
}
