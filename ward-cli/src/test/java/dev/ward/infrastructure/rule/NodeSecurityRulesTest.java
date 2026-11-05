package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The bundled TypeScript and JavaScript security rules, on the scenarios of their specification. */
class NodeSecurityRulesTest {

  private static final String COMMAND = "/command-injection";
  private static final String PATH = "/path-traversal";
  private static final String XSS = "/xss";

  /** Scenario: Command injection from an Express query, through an assignment to a variable. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void commandInjectionFromExpressQuery(final AnalysisLanguage language) {
    final List<AnalysisFinding> found =
        findings(
            language,
            """
            import { exec } from 'child_process';
            export function handle(req) {
              const cmd = req.query.cmd;
              const full = 'ls ' + cmd;
              exec(full);
            }
            """);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).ruleId()).endsWith(COMMAND);
    assertThat(found.get(0).line()).isEqualTo(5);
    assertThat(messages(found.get(0)))
        .containsExactly("source: query", "assigned to full", "sink: exec argument 0");
  }

  /** A value read straight into a variable keeps the two steps that #206 records for it. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void directlyAssignedSourceHasTwoSteps(final AnalysisLanguage language) {
    final List<AnalysisFinding> found =
        findings(
            language,
            """
            import { exec } from 'child_process';
            export function handle(req) {
              const cmd = req.query.cmd;
              exec(cmd);
            }
            """);
    assertThat(found).hasSize(1);
    assertThat(messages(found.get(0))).containsExactly("source: query", "sink: exec argument 0");
  }

  /** A parameter typed Request from express is a source under any name. */
  @Test
  void typedRequestUnderAnyNameIsASource() {
    final List<AnalysisFinding> found =
        findings(
            AnalysisLanguage.TYPESCRIPT,
            """
            import { exec } from 'child_process';
            import { Request } from 'express';
            export function handle(incoming: Request) {
              const full = 'ls ' + incoming.query.cmd;
              exec(full);
            }
            """);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).ruleId()).endsWith(COMMAND);
  }

  /** Scenario: The same flow through a require() binding and a namespace import. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void commandInjectionThroughRequireAndNamespaceImport(final AnalysisLanguage language) {
    final String body = "  const cmd = req.query.cmd;\n  cp.exec(cmd);\n}\n";
    final List<AnalysisFinding> required =
        findings(
            language,
            "const cp = require('child_process');\nexport function handle(req) {\n" + body);
    final List<AnalysisFinding> namespaced =
        findings(
            language,
            "import * as cp from 'node:child_process';\nexport function handle(req) {\n" + body);
    assertThat(required).hasSize(1);
    assertThat(namespaced).hasSize(1);
    assertThat(required.get(0).ruleId()).endsWith(COMMAND);
  }

  /** Scenario: Same name from another module is not matched. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void sameNameFromAnotherModuleIsNotReported(final AnalysisLanguage language) {
    final List<AnalysisFinding> found =
        findings(
            language,
            """
            import { exec } from 'shelljs';
            export function handle(req) {
              const cmd = req.query.cmd;
              exec(cmd);
            }
            """);
    assertThat(found).noneMatch(finding -> finding.ruleId().endsWith(COMMAND));
  }

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
    assertThat(found.get(0).line()).isEqualTo(4);
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
    assertThat(found.get(0).line()).isEqualTo(3);
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

  private static List<AnalysisFinding> findings(final AnalysisLanguage language, final String text) {
    final String path = language == AnalysisLanguage.TYPESCRIPT ? "src/a.ts" : "src/a.js";
    return NodeSecurityRules.run(new SourceUnit(path, language, text));
  }

  private static List<String> messages(final AnalysisFinding finding) {
    return finding.path().stream().map(FlowStep::message).toList();
  }
}
