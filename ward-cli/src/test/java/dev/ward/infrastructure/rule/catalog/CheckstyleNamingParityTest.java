package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in parity check: runs real Checkstyle 14.3.0 on the naming fixtures and compares each
 * violating fixture's positions with the expected ones. Skipped unless {@code
 * -Dward.checkstyle.classpath} names the jars of Checkstyle 14.3.0 and its dependencies. Known
 * differences are listed with their reason; any other difference fails.
 */
class CheckstyleNamingParityTest {

  private static final String CLASSPATH = System.getProperty("ward.checkstyle.classpath");
  private static final String GOOGLE_TYPE_PARAMETER =
      "(^[A-Z][0-9]?$)|(^[A-Z]T$)|(^([A-Z][a-z0-9]+)+T$)";
  private static final String GOOGLE_NAME = "^(_|[a-z]([a-z0-9][a-zA-Z0-9]*)?)$";
  private static final Pattern FINDING = Pattern.compile(":(\\d+):(\\d+): ");

  /** One Ward rule and the Checkstyle module that mirrors it, with the module's properties. */
  record Mirror(String rule, String module, Map<String, String> properties) {}

  /** Ward positions that Checkstyle 14.3.0 does not report, with the reason. */
  private static final Map<String, Set<String>> WARD_ONLY =
      Map.of("local-final-variable-name", Set.of("2:22"));

  /** Checkstyle positions that name the declaration start, not the name: compared by line only. */
  private static final Set<String> LINE_ONLY = Set.of("abstract-class-name");

  private static final List<Mirror> MIRRORS =
      List.of(
          new Mirror(
              "abbreviation-as-word-in-name",
              "AbbreviationAsWordInName",
              Map.of("ignoreFinal", "false", "allowedAbbreviationLength", "0")),
          new Mirror("abstract-class-name", "AbstractClassName", Map.of()),
          new Mirror("catch-parameter-name", "CatchParameterName", Map.of("format", GOOGLE_NAME)),
          new Mirror(
              "class-type-parameter-name",
              "ClassTypeParameterName",
              Map.of("format", GOOGLE_TYPE_PARAMETER)),
          new Mirror("constant-name", "ConstantName", Map.of()),
          new Mirror("google-method-name", "GoogleMethodName", Map.of()),
          new Mirror("google-non-constant-field-name", "GoogleNonConstantFieldName", Map.of()),
          new Mirror("illegal-identifier-name", "IllegalIdentifierName", Map.of()),
          new Mirror(
              "interface-type-parameter-name",
              "InterfaceTypeParameterName",
              Map.of("format", GOOGLE_TYPE_PARAMETER)),
          new Mirror("lambda-parameter-name", "LambdaParameterName", Map.of("format", GOOGLE_NAME)),
          new Mirror(
              "local-final-variable-name",
              "LocalFinalVariableName",
              Map.of(
                  "tokens", "VARIABLE_DEF, PARAMETER_DEF, RESOURCE",
                  "format", "^(_|[a-z](?=[a-z0-9]|$)(?:[a-zA-Z]|[0-9]+(?:_[0-9]+)*)*)$")),
          new Mirror(
              "method-type-parameter-name",
              "MethodTypeParameterName",
              Map.of("format", GOOGLE_TYPE_PARAMETER)),
          new Mirror(
              "package-name", "PackageName", Map.of("format", "^[a-z]+(\\.[a-z][a-z0-9]*)*$")),
          new Mirror("pattern-variable-name", "PatternVariableName", Map.of("format", GOOGLE_NAME)),
          new Mirror(
              "record-component-name",
              "RecordComponentName",
              Map.of("format", "^[a-z]([a-z0-9][a-zA-Z0-9]*)?$")),
          new Mirror(
              "record-type-parameter-name",
              "RecordTypeParameterName",
              Map.of("format", GOOGLE_TYPE_PARAMETER)),
          new Mirror("static-variable-name", "StaticVariableName", Map.of()));

  @Test
  void checkstyleReportsTheSamePositionsAsEveryNamingFixture()
      throws IOException, InterruptedException {
    Assumptions.assumeTrue(CLASSPATH != null, "set -Dward.checkstyle.classpath to run");
    final List<String> problems = new ArrayList<>();
    for (final Mirror mirror : MIRRORS) {
      final Path directory = RuleFixtureSet.root().resolve("java").resolve(mirror.rule());
      final Path config = Files.createTempFile("checkstyle-" + mirror.rule(), ".xml");
      Files.writeString(config, config(mirror), StandardCharsets.UTF_8);
      final List<String> expected =
          Files.readAllLines(directory.resolve("expected.txt")).stream()
              .filter(line -> !line.isBlank())
              .toList();
      final Set<String> found = positions(config, directory.resolve("violating.java"));
      final Set<String> wanted = new java.util.HashSet<>(expected);
      wanted.removeAll(WARD_ONLY.getOrDefault(mirror.rule(), Set.of()));
      if (LINE_ONLY.contains(mirror.rule())) {
        assertThat(found.stream().map(p -> p.split(":")[0]).toList())
            .as(mirror.rule())
            .containsExactlyElementsOf(wanted.stream().map(p -> p.split(":")[0]).toList());
      } else if (!found.equals(wanted)) {
        problems.add(mirror.rule() + " checkstyle=" + found + " ward=" + wanted);
      }
      assertThat(positions(config, directory.resolve("passing.java")))
          .as("checkstyle on passing %s", mirror.rule())
          .isEmpty();
    }
    assertThat(problems).isEmpty();
  }

  private static String config(final Mirror mirror) {
    final StringBuilder properties = new StringBuilder();
    mirror
        .properties()
        .forEach(
            (key, value) ->
                properties.append(
                    "<property name=\"%s\" value=\"%s\"/>".formatted(key, escape(value))));
    return "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE module PUBLIC \"-//Checkstyle//DTD Checkstyle Configuration 1.3//EN\""
        + " \"https://checkstyle.org/dtds/configuration_1_3.dtd\">"
        + "<module name=\"Checker\"><module name=\"TreeWalker\">"
        + "<module name=\""
        + mirror.module()
        + "\">"
        + properties
        + "</module>"
        + "</module></module>";
  }

  private static String escape(final String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
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
    final Set<String> found = new java.util.HashSet<>();
    for (final String line : output.split("\n")) {
      final Matcher matcher = FINDING.matcher(line);
      if (matcher.find()) {
        found.add(matcher.group(1) + ":" + matcher.group(2));
      }
    }
    return found;
  }
}
