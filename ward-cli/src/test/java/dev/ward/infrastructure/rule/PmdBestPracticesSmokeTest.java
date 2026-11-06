package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URISyntaxException;
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
import org.junit.jupiter.api.Test;

/**
 * Runs a real PMD command-line tool with each PMD best-practice rule that Ward implements over its
 * fixture directory, and compares the lines PMD reports for {@code violating.java} with the lines
 * in the fixture's {@code expected.txt}. Skipped unless {@code -Dward.pmd.binary} names the {@code
 * pmd} executable of the PMD version the repository pins. Every difference is listed in the failure
 * and belongs in the pull request with its reason.
 */
class PmdBestPracticesSmokeTest {

  private static final String BINARY_PROPERTY = "ward.pmd.binary";
  private static final String RULESET_PREFIX = "category/java/bestpractices.xml/";
  private static final int NO_VIOLATIONS = 0;
  private static final int VIOLATIONS_FOUND = 4;
  private static final String VIOLATING = "violating.java";
  private static final Pattern POSITION = Pattern.compile("^(\\d+):\\d+$");
  private static final Pattern FIELD = Pattern.compile("\"((?:[^\"]|\"\")*)\"");

  /** Maps each fixture directory of a Ward rule to the PMD rule of the same behaviour. */
  private static final Map<String, String> RULES =
      Map.ofEntries(
          Map.entry("assert-statement-in-test", "AssertStatementInTest"),
          Map.entry("avoid-message-digest-field", "AvoidMessageDigestField"),
          Map.entry("avoid-print-stack-trace", "AvoidPrintStackTrace"),
          Map.entry("avoid-string-buffer-field", "AvoidStringBufferField"),
          Map.entry("avoid-using-hard-coded-ip", "AvoidUsingHardCodedIP"),
          Map.entry("constants-in-interface", "ConstantsInInterface"),
          Map.entry("for-loop-variable-count", "ForLoopVariableCount"),
          Map.entry(
              "j-unit4-suites-should-use-suite-annotation", "JUnit4SuitesShouldUseSuiteAnnotation"),
          Map.entry(
              "j-unit-jupiter-test-should-be-package-private",
              "JUnitJupiterTestShouldBePackagePrivate"),
          Map.entry("j-unit-use-expected", "JUnitUseExpected"),
          Map.entry("labeled-statement", "LabeledStatement"),
          Map.entry("literals-first-in-comparisons", "LiteralsFirstInComparisons"),
          Map.entry("loose-coupling", "LooseCoupling"),
          Map.entry("primitive-wrapper-instantiation", "PrimitiveWrapperInstantiation"),
          Map.entry("reliance-on-default-charset", "RelianceOnDefaultCharset"),
          Map.entry("replace-enumeration-with-iterator", "ReplaceEnumerationWithIterator"),
          Map.entry("replace-hashtable-with-map", "ReplaceHashtableWithMap"),
          Map.entry("replace-java-util-vector", "ReplaceJavaUtilVector"),
          Map.entry("simplifiable-test-assertion", "SimplifiableTestAssertion"),
          Map.entry("system-println", "SystemPrintln"),
          Map.entry(
              "unit-test-assertions-should-include-message",
              "UnitTestAssertionsShouldIncludeMessage"),
          Map.entry("unnecessary-varargs-array-creation", "UnnecessaryVarargsArrayCreation"),
          Map.entry("use-collection-is-empty", "UseCollectionIsEmpty"),
          Map.entry("use-standard-charsets", "UseStandardCharsets"),
          Map.entry("use-try-with-resources", "UseTryWithResources"),
          Map.entry("use-varargs", "UseVarargs"),
          Map.entry("while-loop-with-literal-boolean", "WhileLoopWithLiteralBoolean"));

  @Test
  void aRealPmdReportsTheFixtureLines()
      throws IOException, URISyntaxException, InterruptedException {
    final String binary = System.getProperty(BINARY_PROPERTY);
    assumeTrue(binary != null && !binary.isBlank(), "set -D" + BINARY_PROPERTY + " to run it");
    final Path root = Path.of(PmdBestPracticesSmokeTest.class.getResource("/rules/java").toURI());
    final List<String> differences = new ArrayList<>();
    for (final Map.Entry<String, String> rule : RULES.entrySet()) {
      final Path directory = root.resolve(rule.getKey());
      final Set<String> expected = expectedLines(directory.resolve("expected.txt"));
      final Set<String> reported = reportedLines(binary, directory, rule.getValue());
      if (!expected.equals(reported)) {
        differences.add(rule.getKey() + ": fixture " + expected + ", PMD " + reported);
      }
    }
    assertThat(differences).isEmpty();
  }

  /** Returns the line numbers of the fixture's expected positions. */
  private static Set<String> expectedLines(final Path file) throws IOException {
    final Set<String> lines = new HashSet<>();
    for (final String position : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      final Matcher match = POSITION.matcher(position.strip());
      if (match.matches()) {
        lines.add(match.group(1));
      }
    }
    return lines;
  }

  /** Returns the line numbers PMD reports for the violating fixture under one rule. */
  private static Set<String> reportedLines(
      final String binary, final Path directory, final String pmdRule)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
                binary,
                "check",
                "--dir",
                directory.toString(),
                "--rulesets",
                RULESET_PREFIX + pmdRule,
                "--format",
                "csv")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final int exit = process.waitFor();
    assertThat(exit).isIn(NO_VIOLATIONS, VIOLATIONS_FOUND);
    return violatingLines(output);
  }

  /** Reads the line of each PMD CSV record that is in the violating fixture. */
  private static Set<String> violatingLines(final String csv) {
    final List<String> lines = csv.lines().toList();
    if (lines.isEmpty()) {
      return Set.of();
    }
    final List<String> header = fields(lines.get(0));
    final Set<String> reported = new HashSet<>();
    for (final String record : lines.subList(1, lines.size())) {
      final List<String> fields = fields(record);
      if (fields.size() > header.indexOf("line")
          && fields.get(header.indexOf("file")).endsWith(VIOLATING)) {
        reported.add(fields.get(header.indexOf("line")));
      }
    }
    return reported;
  }

  /** Returns the quoted fields of one CSV line, with doubled quotes unescaped. */
  private static List<String> fields(final String line) {
    final List<String> fields = new ArrayList<>();
    final Matcher field = FIELD.matcher(line);
    while (field.find()) {
      fields.add(field.group(1).replace("\"\"", "\""));
    }
    return fields;
  }
}
