package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Compares {@code ward:java/unused-assignment} with the committed PMD {@code UnusedAssignment}
 * reference on the Java data-flow fixtures. The reference is produced by running PMD itself; see
 * the data-flow section of AGENTS.md.
 */
class UnusedAssignmentPmdReferenceTest {

  static final String FIXTURES = "dataflow/java";
  static final String REFERENCE = FIXTURES + "/pmd-unused-assignment.json";
  private static final String RULE_ID = "ward:java/unused-assignment";
  private static final Pattern VARIABLE = Pattern.compile("'([^']+)'");

  @Test
  void wardReportsTheSameVariablesAndLinesAsPmd() throws IOException, URISyntaxException {
    final RuleRunResult result = run();

    assertThat(result.executionErrors()).isEmpty();
    assertThat(wardRows(result.findings())).isEqualTo(pmdRows());
  }

  private static List<String> wardRows(final List<AnalysisFinding> findings) {
    return findings.stream()
        .filter(finding -> RULE_ID.equals(finding.ruleId()))
        .map(UnusedAssignmentPmdReferenceTest::wardRow)
        .sorted()
        .toList();
  }

  private static String wardRow(final AnalysisFinding finding) {
    final Matcher matcher = VARIABLE.matcher(finding.message());
    assertThat(matcher.find()).as("a variable name in %s", finding.message()).isTrue();
    return row(finding.file(), finding.line(), matcher.group(1));
  }

  static List<String> pmdRows() throws IOException, URISyntaxException {
    final JsonTree reference = JsonTree.parse(Files.readString(resource(REFERENCE)));
    final List<String> rows = new ArrayList<>();
    for (int index = 0; index < reference.size(); index++) {
      final JsonTree entry = reference.get(index);
      rows.add(
          row(
              entry.get("file").asString(),
              entry.get("line").asInt(),
              entry.get("variable").asString()));
    }
    return rows.stream().sorted(Comparator.naturalOrder()).toList();
  }

  static String row(final String file, final Integer line, final String variable) {
    return file + ":" + line + ":" + variable;
  }

  private static RuleRunResult run() throws IOException, URISyntaxException {
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream().filter(rule -> RULE_ID.equals(rule.id())).toList();
    final FrontendSupport java =
        new FrontendSupport(
            AnalysisLanguage.JAVA,
            new AntlrLanguageFrontend(),
            Set.of(RuleCapability.SYNTAX, RuleCapability.CFG, RuleCapability.DATAFLOW),
            new JavaControlFlow());
    return new RuleEngine(BundledRuleMatchers.create()).run(units(), List.of(java), rules);
  }

  private static List<SourceUnit> units() throws IOException, URISyntaxException {
    try (Stream<Path> files = Files.list(resource(FIXTURES))) {
      return files
          .filter(path -> path.toString().endsWith(".java"))
          .sorted()
          .map(UnusedAssignmentPmdReferenceTest::unit)
          .toList();
    }
  }

  private static SourceUnit unit(final Path path) {
    try {
      return new SourceUnit(
          path.getFileName().toString(), AnalysisLanguage.JAVA, Files.readString(path));
    } catch (final IOException exception) {
      throw new IllegalStateException("cannot read fixture " + path, exception);
    }
  }

  static Path resource(final String name) throws IOException, URISyntaxException {
    final URL url = UnusedAssignmentPmdReferenceTest.class.getResource("/" + name);
    if (url == null) {
      throw new IOException("missing test resource " + name);
    }
    return Path.of(url.toURI());
  }
}
