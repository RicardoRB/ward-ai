package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The bundled path-traversal and XSS rules, on the scenarios of their specification. */
class NodeSinkRulesTest {

  private static final String PATH = "/path-traversal";
  private static final String XSS = "/xss";
  private static final int PATH_LINE = 4;
  private static final int XSS_LINE = 3;

  /** Scenario: Path traversal from req.params into fs.readFile. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void pathTraversalIntoReadFile(final AnalysisLanguage language) {
    final List<AnalysisFinding> found =
        findings(
            language,
            """
            import * as fs from 'fs';
            export function handle(req) {
              const name = req.params.name;
              fs.readFile(name, () => {});
            }
            """);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).ruleId()).endsWith(PATH);
    assertThat(found.get(0).line()).isEqualTo(PATH_LINE);
    assertThat(messages(found.get(0)))
        .containsExactly("source: params", "sink: readFile argument 0");
  }

  /** Scenario: Path traversal into res.sendFile. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void pathTraversalIntoSendFile(final AnalysisLanguage language) {
    final List<AnalysisFinding> found =
        findings(
            language,
            """
            export function handle(req, res) {
              const name = req.params.name;
              res.sendFile(name);
            }
            """);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).ruleId()).endsWith(PATH);
  }

  /** Scenario: XSS through innerHTML in browser code. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void xssThroughInnerHtml(final AnalysisLanguage language) {
    final List<AnalysisFinding> found =
        findings(
            language,
            """
            export function show(el) {
              const q = location.search;
              el.innerHTML = q;
            }
            """);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).ruleId()).endsWith(XSS);
    assertThat(found.get(0).line()).isEqualTo(XSS_LINE);
    assertThat(messages(found.get(0)))
        .containsExactly("source: search", "sink: innerHTML argument 0");
  }

  /** Scenario: Sanitized value through encodeURIComponent before an HTML sink. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void sanitizedValueIsNotReported(final AnalysisLanguage language) {
    final List<AnalysisFinding> found =
        findings(
            language,
            """
            export function show(el) {
              const q = location.search;
              el.innerHTML = encodeURIComponent(q);
            }
            """);
    assertThat(found).noneMatch(finding -> finding.ruleId().endsWith(XSS));
  }

  private static List<AnalysisFinding> findings(
      final AnalysisLanguage language, final String text) {
    final String path = language == AnalysisLanguage.TYPESCRIPT ? "src/a.ts" : "src/a.js";
    return NodeSecurityRules.run(new SourceUnit(path, language, text));
  }

  private static List<String> messages(final AnalysisFinding finding) {
    return finding.path().stream().map(FlowStep::message).toList();
  }
}
