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
 * Opt-in parity check: runs real Checkstyle 14.3.0 on the import and module fixtures and compares
 * each violating fixture's positions with the expected ones. Skipped unless {@code
 * -Dward.checkstyle.classpath} names the jars of Checkstyle 14.3.0 and its dependencies. The
 * differences that remain are listed with their reason in {@link #LINE_ONLY}; any other difference
 * fails.
 */
class ImportCheckstyleParityTest {

  private static final String CLASSPATH = System.getProperty("ward.checkstyle.classpath");
  private static final String CUSTOM_RULES = "STATIC###THIRD_PARTY_PACKAGE";
  private static final String GOOGLE_STANDARD = "^(java|javax)\\.";
  private static final String ANY = ".*";
  private static final String NO_SPECIAL = "^$";
  private static final Pattern FINDING = Pattern.compile(":(\\d+):(\\d+): ");
  private static final String FILTER_SUN = "sun\\..*";

  /**
   * Ward cases whose Checkstyle columns differ, with the reason: Checkstyle reports at the last dot
   * of the name, Ward at the start of the declaration, so only the lines are compared.
   */
  private static final Map<String, String> LINE_ONLY =
      Map.of(
          "static-import",
          "Checkstyle reports the last dot of the name",
          "static-import-excludes",
          "Checkstyle reports the last dot of the name",
          "unnecessary-fully-qualified-type",
          "Checkstyle reports the last dot before the type name");

  /** One Ward rule case and the Checkstyle module that mirrors it, with the module's properties. */
  record Mirror(String rule, String fixture, String module, Map<String, String> properties) {}

  private static final List<Mirror> MIRRORS =
      List.of(
          new Mirror("static-import", "", "AvoidStaticImport", Map.of()),
          new Mirror(
              "static-import",
              "excludes",
              "AvoidStaticImport",
              Map.of("excludes", "java.lang.Math.*")),
          new Mirror("illegal-import", "", "IllegalImport", Map.of()),
          new Mirror("illegal-import", "pkgs", "IllegalImport", Map.of("illegalPkgs", "java.util")),
          new Mirror(
              "illegal-import",
              "classes",
              "IllegalImport",
              Map.of("illegalPkgs", "", "illegalClasses", "java.util.List")),
          new Mirror(
              "illegal-import",
              "regexp",
              "IllegalImport",
              Map.of("regexp", "true", "illegalPkgs", FILTER_SUN)),
          new Mirror("redundant-import", "", "RedundantImport", Map.of()),
          new Mirror(
              "unnecessary-fully-qualified-type", "", "UnnecessaryFullyQualifiedType", Map.of()),
          new Mirror("import-order", "", "ImportOrder", Map.of()),
          new Mirror("import-order", "groups", "ImportOrder", Map.of("groups", "javax, java")),
          new Mirror("import-order", "ordered", "ImportOrder", Map.of("ordered", "false")),
          new Mirror(
              "import-order",
              "separated",
              "ImportOrder",
              Map.of("groups", "java, javax", "separated", "true")),
          new Mirror(
              "import-order", "case-sensitive", "ImportOrder", Map.of("caseSensitive", "false")),
          new Mirror(
              "custom-import-order",
              "",
              "CustomImportOrder",
              customProperties(CUSTOM_RULES, "true", "true", NO_SPECIAL, GOOGLE_STANDARD, ANY)),
          new Mirror(
              "custom-import-order",
              "rules",
              "CustomImportOrder",
              customProperties(
                  "THIRD_PARTY_PACKAGE###STATIC",
                  "true",
                  "true",
                  NO_SPECIAL,
                  GOOGLE_STANDARD,
                  ANY)),
          new Mirror(
              "custom-import-order",
              "sort",
              "CustomImportOrder",
              customProperties(CUSTOM_RULES, "false", "true", NO_SPECIAL, GOOGLE_STANDARD, ANY)),
          new Mirror(
              "custom-import-order",
              "separate",
              "CustomImportOrder",
              customProperties(CUSTOM_RULES, "true", "false", NO_SPECIAL, GOOGLE_STANDARD, ANY)),
          new Mirror(
              "custom-import-order",
              "special",
              "CustomImportOrder",
              customProperties(
                  "SPECIAL_IMPORTS###STANDARD_JAVA_PACKAGE###THIRD_PARTY_PACKAGE",
                  "true",
                  "true",
                  "^org\\.example\\..*",
                  GOOGLE_STANDARD,
                  ANY)),
          new Mirror(
              "custom-import-order",
              "standard",
              "CustomImportOrder",
              customProperties(
                  "STANDARD_JAVA_PACKAGE###THIRD_PARTY_PACKAGE",
                  "true",
                  "true",
                  NO_SPECIAL,
                  "^org\\.example\\..*",
                  ANY)),
          new Mirror(
              "custom-import-order",
              "third",
              "CustomImportOrder",
              customProperties(
                  "STANDARD_JAVA_PACKAGE###THIRD_PARTY_PACKAGE",
                  "true",
                  "true",
                  NO_SPECIAL,
                  GOOGLE_STANDARD,
                  "^org\\..*")),
          new Mirror("module-directive-order", "", "ModuleDirectiveOrder", Map.of()),
          new Mirror(
              "module-directive-order",
              "order",
              "ModuleDirectiveOrder",
              Map.of("order", "exports, requires")),
          new Mirror(
              "module-directive-order",
              "separation",
              "ModuleDirectiveOrder",
              Map.of("validateBlockSeparation", "false")),
          new Mirror("module-import-order", "", "ModuleImportOrder", Map.of()),
          new Mirror(
              "module-import-order", "option", "ModuleImportOrder", Map.of("option", "bottom")),
          new Mirror(
              "module-import-order", "separated", "ModuleImportOrder", Map.of("separated", "true")),
          new Mirror("avoid-module-import", "", "AvoidModuleImport", Map.of()),
          new Mirror(
              "avoid-module-import",
              "excludes",
              "AvoidModuleImport",
              Map.of("excludes", "java.base")),
          new Mirror(
              "avoid-module-import",
              "max",
              "AvoidModuleImport",
              Map.of("maxAllowedModuleImports", "1")));

  private static Map<String, String> customProperties(
      final String rules,
      final String sort,
      final String separate,
      final String special,
      final String standard,
      final String thirdParty) {
    return Map.of(
        "customImportOrderRules", rules,
        "sortImportsInGroupAlphabetically", sort,
        "separateLineBetweenGroups", separate,
        "specialImportsRegExp", special,
        "standardPackageRegExp", standard,
        "thirdPartyPackageRegExp", thirdParty);
  }

  @Test
  void checkstyleReportsTheSamePositionsAsEveryImportFixture()
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
      if (LINE_ONLY.containsKey(key)) {
        if (!lines(found).equals(lines(expected))) {
          problems.add(key + " checkstyle=" + found + " ward=" + expected);
        }
      } else if (!found.equals(expected)) {
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
    final Set<String> found = new HashSet<>();
    for (final String line : output.split("\n")) {
      final Matcher matcher = FINDING.matcher(line);
      if (matcher.find()) {
        found.add(matcher.group(1) + ":" + matcher.group(2));
      }
    }
    return found;
  }
}
