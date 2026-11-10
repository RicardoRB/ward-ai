package dev.ward.lang.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Parses the Swift corpus with the frontend and compares the parse report with the committed one. A
 * change in the grammar shows up as a change in {@code corpus/parse-report.txt}.
 */
final class SwiftCorpusTest {

  private static final String CORPUS = "corpus/";
  private static final List<String> SOURCES =
      List.of(
          "async-await.swift",
          "generics-where.swift",
          "macros-freestanding.swift",
          "opaque-existential.swift",
          "optional-binding.swift",
          "plain-types.swift",
          "property-wrappers.swift",
          "result-builders.swift");

  @Test
  void parseReportMatchesTheCommittedReport() throws IOException {
    final String report =
        SOURCES.stream()
            .map(SwiftCorpusTest::reportLine)
            .collect(Collectors.joining("\n", "", "\n"));

    assertThat(report).isEqualTo(SwiftRuleRuns.read(CORPUS + "parse-report.txt"));
  }

  @Test
  void syntaxErrorFixtureIsReportedWithItsFirstErrorLocation() throws IOException {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(
                new SourceUnit(
                    "broken.swift",
                    AnalysisLanguage.SWIFT,
                    SwiftRuleRuns.read("fixtures/swift-structural/syntax-error.swift")));

    assertThat(result.diagnostics()).isNotEmpty();
  }

  /**
   * One line per source: its name, then {@code parsed} or {@code error} with the first location.
   */
  private static String reportLine(final String source) {
    try {
      final ParseResult result =
          new AntlrLanguageFrontend()
              .parse(
                  new SourceUnit(
                      source, AnalysisLanguage.SWIFT, SwiftRuleRuns.read(CORPUS + source)));
      if (result.diagnostics().isEmpty()) {
        return source + "\tparsed";
      }
      final AnalysisFinding first = result.diagnostics().get(0);
      return source + "\terror\t" + first.line() + ":" + first.column();
    } catch (final IOException exception) {
      throw new IllegalStateException("cannot read " + source, exception);
    }
  }
}
