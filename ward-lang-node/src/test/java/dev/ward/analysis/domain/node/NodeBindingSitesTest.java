package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowDump;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Checks the receiver types of module bindings, and the member-write and constructor sites, in
 * TypeScript and JavaScript.
 */
class NodeBindingSitesTest {

  private static final String IMPORTS =
      """
      import { exec as run } from 'child_process';
      import cp from 'node:child_process';
      import * as fsp from 'fs/promises';
      import fs from 'fs';
      function go(x) {
        run(x);
        cp.exec(x);
        fsp.readFile(x);
        fs.promises.readFile(x);
      }
      """;

  private static final String REQUIRES =
      """
      const child = require('child_process');
      const { execFile: ef } = require('node:child_process');
      function go(x) {
        child.exec(x);
        ef(x);
      }
      """;

  private static final String WRITES =
      """
      function go(el, html) {
        el.innerHTML = sanitize(html);
        el['outerHTML'] = html;
        el.title += html;
        if (el.a == html) { el.b; }
      }
      """;

  /** Scenario: Aliased named import. The call is listed under the imported name. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void aliasedNamedImportCallsTheImportedNameFromItsModule(final AnalysisLanguage language) {
    final FlowSite call = siteOn(graph(language, IMPORTS), "exec");
    assertThat(call.receiverType()).contains("child_process");
  }

  /** Scenario: Default import. A qualifier bound to a default import is its module. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void defaultImportQualifierResolvesToItsModule(final AnalysisLanguage language) {
    final FlowSite call = siteOn(graph(language, IMPORTS), "exec", "cp");
    assertThat(call.receiverType()).contains("child_process");
  }

  /** Scenario: Namespace import. The module keeps its path, so fs/promises stays distinct. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void namespaceImportKeepsTheModulePath(final AnalysisLanguage language) {
    final FlowSite call = siteOn(graph(language, IMPORTS), "readFile", "fsp");
    assertThat(call.receiverType()).contains("fs/promises");
  }

  /** Scenario: Dotted qualifier. The first segment names the module, so fs.promises is fs. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void dottedQualifierResolvesToItsRootModule(final AnalysisLanguage language) {
    final FlowSite call = siteOn(graph(language, IMPORTS), "readFile", "fs.promises");
    assertThat(call.receiverType()).contains("fs");
  }

  /** Scenario: require. A variable bound to require() is its module. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void requireBindingResolvesToItsModule(final AnalysisLanguage language) {
    final FlowSite call = siteOn(graph(language, REQUIRES), "exec", "child");
    assertThat(call.receiverType()).contains("child_process");
  }

  /** Scenario: Destructured require. The call is listed under the required name. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void destructuredRequireCallsTheRequiredName(final AnalysisLanguage language) {
    final FlowSite call = siteOn(graph(language, REQUIRES), "execFile");
    assertThat(call.receiverType()).contains("child_process");
  }

  /** Scenario: Member write. The site names the property, its qualifier and the value. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void memberWriteNamesPropertyAndValue(final AnalysisLanguage language) {
    final FlowSite write = writeOn(language, "innerHTML");
    assertThat(write.qualifier()).isEqualTo("el");
    assertThat(argumentText(write, WRITES)).isEqualTo("sanitize(html)");
  }

  /** Scenario: Member write after the sites in the value. The write comes last. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void memberWriteIsListedAfterTheSitesInItsValue(final AnalysisLanguage language) {
    final List<String> names =
        sitesOf(graph(language, WRITES)).stream()
            .map(site -> site.kind() + ":" + site.name())
            .toList();
    assertThat(names.indexOf("CALL:sanitize"))
        .isLessThan(names.indexOf(FlowSiteKind.MEMBER_WRITE + ":innerHTML"));
  }

  /** Scenario: Bracket key. A string literal key names the property. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void bracketKeyNamesTheProperty(final AnalysisLanguage language) {
    final FlowSite write = writeOn(language, "outerHTML");
    assertThat(argumentText(write, WRITES)).isEqualTo("html");
  }

  /** Scenario: Compound assignment writes the property too. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void compoundAssignmentIsAMemberWrite(final AnalysisLanguage language) {
    assertThat(writeOn(language, "title").qualifier()).isEqualTo("el");
  }

  /** Scenario: Comparison is not a write. Only assignment operators write a property. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void comparisonIsNotAMemberWrite(final AnalysisLanguage language) {
    final List<FlowSite> writes =
        sitesOf(graph(language, WRITES)).stream()
            .filter(site -> site.kind() == FlowSiteKind.MEMBER_WRITE && site.name().equals("a"))
            .toList();
    assertThat(writes).isEmpty();
  }

  /** Scenario: Constructor site. A new expression is a call named <init> on its class. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void newExpressionIsAConstructorCall(final AnalysisLanguage language) {
    final String source = "function go(body) { const f = new Function(body); }";
    final FlowSite init = siteOn(graph(language, source), "<init>", "Function");
    assertThat(init.qualifier()).isEqualTo("Function");
    assertThat(init.receiverType()).isEmpty();
    assertThat(init.arguments()).hasSize(1);
  }

  private static ControlFlowGraph graph(final AnalysisLanguage language, final String source) {
    return NodeTaintFixtures.graph(language, source, "go");
  }

  private static FlowSite writeOn(final AnalysisLanguage language, final String name) {
    return sitesOf(graph(language, WRITES)).stream()
        .filter(site -> site.kind() == FlowSiteKind.MEMBER_WRITE && site.name().equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "no write of "
                        + name
                        + "\n"
                        + ControlFlowDump.render(graph(language, WRITES))));
  }

  private static FlowSite siteOn(final ControlFlowGraph graph, final String name) {
    return siteOn(graph, name, "");
  }

  private static FlowSite siteOn(
      final ControlFlowGraph graph, final String name, final String qualifier) {
    return sitesOf(graph).stream()
        .filter(site -> site.name().equals(name) && site.qualifier().equals(qualifier))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("no site " + name + " on " + qualifier + "\n" + dump(graph)));
  }

  private static String dump(final ControlFlowGraph graph) {
    return ControlFlowDump.render(graph);
  }

  private static List<FlowSite> sitesOf(final ControlFlowGraph graph) {
    return graph.blocks().stream().map(BasicBlock::sites).flatMap(List::stream).toList();
  }

  private static String argumentText(final FlowSite site, final String source) {
    final Span span = site.arguments().get(0);
    return source.substring(span.startOffset(), span.endOffset());
  }
}
