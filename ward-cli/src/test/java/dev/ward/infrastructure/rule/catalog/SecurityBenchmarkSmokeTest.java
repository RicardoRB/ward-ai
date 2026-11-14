package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.catalog.JulietMethods.Kind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the Java security rules over the Juliet Java 1.3 test suite and compares the per-weakness
 * counts with the committed report {@code benchmark/security-report.txt}. It is skipped unless
 * {@code -Dward.benchmark.juliet=<extracted suite>} is set; {@code -Dward.benchmark.juliet.zip}
 * names the archive to check, and {@code -Dward.benchmark.write=true} rewrites the report.
 *
 * <p>A finding on a line of a {@code bad} method is a true positive and one on a line of a {@code
 * good} method is a false positive. Most Juliet cases move the value across methods or classes, so
 * recall of the taint rules is limited to flows inside one file.
 */
class SecurityBenchmarkSmokeTest {

  private static final String MAP = "/benchmark/security-cwe-map.csv";
  private static final String REPORT = "benchmark/security-report.txt";
  private static final String SOURCE_ROOT = "src/main/java/";
  private static final Pattern VARIANT = Pattern.compile("^(.*_\\d+)(?:[a-z]|_\\w+)\\.java$");
  private static final String HEADER =
      "# Juliet Java 1.3 (NIST SARD 111, public domain) against Ward's Java security rules.\n"
          + "# A finding in a bad method is a true positive (tp, at most one per case), a bad case"
          + " without one is a false negative (fn), and a finding in a good method is a false"
          + " positive (fp).\n"
          + "# The taint rules follow wildcard imports of JDK and servlet types, calls between the methods"
          + " of one file, and calls into the other files of the case (the a to e variants of one"
          + " flow, given to the run as its project), through a variable, a field or a parameter"
          + " whose class is decided (the class it was created as, else its declared class, and"
          + " none when several are possible), and through an array, a holder object, a collection"
          + " or a builder that received the value. The remaining false negatives are the files"
          + " that only hold a sink or a source (b to e), flows through a static field, a"
          + " serialized value or an abstract receiver that is not created in the body, and the"
          + " cases that read from a class the rules do not model.\n"
          + "# A sink on a chained receiver such as response.getWriter() is matched by the text of"
          + " the receiver. The hard-coded password and key rules follow a string literal through"
          + " a variable of one method.\n"
          + "# The CWE-327 false positives are the good cases that call Cipher.getInstance(\"AES\"),"
          + " which the JCA documentation maps to ECB mode, so ward:java/ecb-mode is right.\n"
          + "# Regenerate: ./mvnw -pl ward-cli -am -Dtest=SecurityBenchmarkSmokeTest"
          + " -Dward.benchmark.juliet=<dir> -Dward.benchmark.write=true"
          + " -Dsurefire.failIfNoSpecifiedTests=false test\n";

  /** One row of the map: a CWE of Juliet and the rules that must find it. */
  private record Row(int cwe, String name, List<String> rules) {}

  /** The counts of one row. */
  private record Counts(int cases, int truePositives, int falsePositives) {}

  @Test
  @EnabledIfSystemProperty(named = "ward.benchmark.juliet", matches = ".+")
  void julietReportMatchesTheCommittedOne() throws IOException {
    final String zip = System.getProperty("ward.benchmark.juliet.zip");
    if (zip != null) {
      assertThat(JulietCorpus.sha256(Path.of(zip))).isEqualTo(JulietCorpus.ARCHIVE_SHA256);
    }
    final JulietCorpus corpus =
        new JulietCorpus(Path.of(System.getProperty("ward.benchmark.juliet")));
    final Map<String, RuleDefinition> rules =
        CatalogRuleDefinitions.all().stream()
            .collect(Collectors.toMap(RuleDefinition::id, Function.identity()));
    final RuleFixtureRunner runner = new RuleFixtureRunner();
    final StringBuilder report = new StringBuilder(HEADER);
    for (final Row row : rows()) {
      final Counts counts = count(corpus, runner, rules, row);
      report.append(line(row, counts));
    }
    final Path file = Path.of("src/test/resources").resolve(REPORT);
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(file, report.toString());
    }
    assertThat(report.toString()).isEqualTo(Files.readString(file));
  }

  private static Counts count(
      final JulietCorpus corpus,
      final RuleFixtureRunner runner,
      final Map<String, RuleDefinition> rules,
      final Row row)
      throws IOException {
    int cases = 0;
    int truePositives = 0;
    int falsePositives = 0;
    for (final Path file : corpus.cases(row.cwe())) {
      final String source = Files.readString(file, StandardCharsets.UTF_8);
      final List<Kind> kinds = JulietMethods.kinds(source);
      final List<AnalysisFinding> findings = findings(runner, rules, row, file, source);
      cases += JulietMethods.hasBad(source) ? 1 : 0;
      truePositives += hit(findings, kinds, Kind.BAD) > 0 ? 1 : 0;
      falsePositives += hit(findings, kinds, Kind.GOOD);
    }
    return new Counts(cases, truePositives, falsePositives);
  }

  private static List<AnalysisFinding> findings(
      final RuleFixtureRunner runner,
      final Map<String, RuleDefinition> rules,
      final Row row,
      final Path file,
      final String source)
      throws IOException {
    final List<AnalysisFinding> found = new ArrayList<>();
    for (final String id : row.rules()) {
      final RuleDefinition rule = rules.get(id);
      assertThat(rule).as("rule %s of the benchmark map", id).isNotNull();
      found.addAll(
          runner.run(
              rule,
              SOURCE_ROOT + file.getFileName(),
              source,
              FixtureTypeFacts.of(Optional.empty()),
              new FixtureProject(ProjectFiles.empty(), siblings(file))));
    }
    return found;
  }

  /**
   * The other files of the case: Juliet splits a flow over {@code ..._54a.java} to {@code
   * ..._54e.java}, and variant 81 over {@code ..._81a.java}, {@code ..._81_base.java} and its
   * subclasses, so the files of one directory with the same name up to the letter or the suffix
   * belong to it.
   */
  private static List<SourceUnit> siblings(final Path file) throws IOException {
    final Matcher variant = VARIANT.matcher(file.getFileName().toString());
    if (!variant.matches()) {
      return List.of();
    }
    final List<SourceUnit> found = new ArrayList<>();
    try (Stream<Path> files = Files.list(file.getParent())) {
      for (final Path other : files.sorted().toList()) {
        final Matcher match = VARIANT.matcher(other.getFileName().toString());
        if (!other.equals(file) && match.matches() && match.group(1).equals(variant.group(1))) {
          found.add(
              new SourceUnit(
                  SOURCE_ROOT + other.getFileName(),
                  AnalysisLanguage.JAVA,
                  Files.readString(other, StandardCharsets.UTF_8)));
        }
      }
    }
    return found;
  }

  private static int hit(
      final List<AnalysisFinding> findings, final List<Kind> kinds, final Kind kind) {
    int hits = 0;
    for (final AnalysisFinding finding : findings) {
      final int index = finding.line() == null ? 0 : finding.line() - 1;
      hits += index >= 0 && index < kinds.size() && kinds.get(index) == kind ? 1 : 0;
    }
    return hits;
  }

  private static String line(final Row row, final Counts counts) {
    return "CWE-%d %s | cases %d | tp %d | fn %d | fp %d | %s%n"
        .formatted(
            row.cwe(),
            row.name(),
            counts.cases(),
            counts.truePositives(),
            counts.cases() - counts.truePositives(),
            counts.falsePositives(),
            String.join(", ", row.rules()));
  }

  private static List<Row> rows() throws IOException {
    try (InputStream in = SecurityBenchmarkSmokeTest.class.getResourceAsStream(MAP)) {
      final List<Row> rows = new ArrayList<>();
      new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(text -> !text.startsWith("#") && !text.isBlank())
          .forEach(
              text -> {
                final String[] parts = text.split(",", 3);
                rows.add(
                    new Row(
                        Integer.parseInt(parts[0].trim()),
                        parts[1].trim(),
                        List.of(parts[2].trim().split(";"))));
              });
      return rows;
    }
  }
}
