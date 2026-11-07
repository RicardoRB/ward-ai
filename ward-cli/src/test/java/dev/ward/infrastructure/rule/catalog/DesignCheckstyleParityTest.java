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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in parity check: runs real Checkstyle 14.3.0 on the class design fixtures and compares each
 * violating fixture's positions with the expected ones. Skipped unless {@code
 * -Dward.checkstyle.classpath} names the jars of Checkstyle 14.3.0 and its dependencies. Each
 * difference that remains is listed with its reason in {@link #KNOWN_DIFFERENCES}; any other
 * difference fails.
 */
class DesignCheckstyleParityTest {

  private static final String CLASSPATH = System.getProperty("ward.checkstyle.classpath");
  private static final Pattern FINDING = Pattern.compile(":(\\d+):(\\d+): ");
  private static final String TRUE = "true";
  private static final String FALSE = "false";

  /**
   * Ward cases whose columns differ from Checkstyle, with the reason. Each one is compared by line
   * only; the reason names where Checkstyle reports instead of where Ward does.
   */
  private static final Map<String, String> KNOWN_DIFFERENCES =
      Map.of(
          "hide-utility-class-constructor",
          "Checkstyle reports the first token of the class, a modifier when it has one; Ward the"
              + " class keyword",
          "mutable-exception",
          "Checkstyle reports the start of the field's modifiers; Ward the field's type",
          "visibility-modifier",
          "Checkstyle reports the field name; Ward the start of the declaration",
          "sealed-should-have-permits-list",
          "Checkstyle reports the sealed modifier; Ward the type keyword",
          "throws-count",
          "Checkstyle reports the throws keyword; Ward the start of the declaration",
          "design-for-extension",
          "Checkstyle reports the start of the method's modifiers; Ward the return type");

  private static final List<Mirror> MIRRORS =
      List.of(
          new Mirror("one-top-level-class", "", "OneTopLevelClass", Map.of()),
          new Mirror("final-class", "", "FinalClass", Map.of()),
          new Mirror("hide-utility-class-constructor", "", "HideUtilityClassConstructor", Map.of()),
          new Mirror(
              "hide-utility-class-constructor",
              "annotated",
              "HideUtilityClassConstructor",
              Map.of("ignoreAnnotatedBy", "Generated")),
          new Mirror("inner-type-last", "", "InnerTypeLast", Map.of()),
          new Mirror("interface-is-type", "", "InterfaceIsType", Map.of()),
          new Mirror(
              "interface-is-type",
              "markers",
              "InterfaceIsType",
              Map.of("allowMarkerInterfaces", FALSE)),
          new Mirror("mutable-exception", "", "MutableException", Map.of()),
          new Mirror(
              "mutable-exception",
              "names",
              "MutableException",
              Map.of("format", "^.*Failure$", "extendedClassNameFormat", "^Exception$")),
          new Mirror(
              "sealed-should-have-permits-list", "", "SealedShouldHavePermitsList", Map.of()),
          new Mirror("throws-count", "", "ThrowsCount", Map.of()),
          new Mirror(
              "throws-count",
              "strict",
              "ThrowsCount",
              Map.of("max", "1", "ignorePrivateMethods", FALSE)),
          new Mirror("visibility-modifier", "", "VisibilityModifier", Map.of()),
          new Mirror(
              "visibility-modifier",
              "allowed",
              "VisibilityModifier",
              Map.of(
                  "allowPublicFinalFields", TRUE,
                  "packageAllowed", TRUE,
                  "protectedAllowed", TRUE,
                  "publicMemberPattern", "^ID$",
                  "ignoreAnnotationCanonicalNames", "Inject")),
          new Mirror("design-for-extension", "", "DesignForExtension", Map.of()),
          new Mirror(
              "design-for-extension",
              "phrase",
              "DesignForExtension",
              Map.of(
                  "ignoredAnnotations", "Override", "requiredJavadocPhrase", "Extension point")));

  @Test
  void checkstyleReportsTheSamePositionsAsEveryDesignFixture()
      throws IOException, InterruptedException {
    Assumptions.assumeTrue(CLASSPATH != null, "set -Dward.checkstyle.classpath to run");
    final List<String> problems = new ArrayList<>();
    for (final Mirror mirror : MIRRORS) {
      final Path directory = RuleFixtureSet.root().resolve("java").resolve(mirror.rule());
      final String suffix = mirror.fixture().isEmpty() ? "" : "-" + mirror.fixture();
      final Path config = Files.createTempFile("checkstyle-" + mirror.rule(), ".xml");
      Files.writeString(config, config(mirror), StandardCharsets.UTF_8);
      final Set<String> expected =
          expectedPositions(directory.resolve("expected" + suffix + ".txt"));
      final Set<String> found =
          positions(config, directory.resolve("violating" + suffix + ".java"));
      final String key = mirror.rule() + suffix;
      final boolean sameLines = lines(found).equals(lines(expected));
      if (KNOWN_DIFFERENCES.containsKey(mirror.rule())) {
        if (!sameLines) {
          problems.add(key + " checkstyle=" + found + " ward=" + expected);
        }
      } else if (!found.equals(expected)) {
        problems.add(key + " checkstyle=" + found + " ward=" + expected);
      }
      if (suffix.isEmpty()) {
        final Set<String> passing = positions(config, directory.resolve("passing.java"));
        if (!passing.isEmpty()) {
          problems.add(mirror.rule() + " passing checkstyle=" + passing);
        }
      }
    }
    assertThat(problems).isEmpty();
  }

  private static Set<String> lines(final Set<String> positions) {
    return positions.stream().map(position -> position.split(":")[0]).collect(Collectors.toSet());
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
        + "</module></module></module>";
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
