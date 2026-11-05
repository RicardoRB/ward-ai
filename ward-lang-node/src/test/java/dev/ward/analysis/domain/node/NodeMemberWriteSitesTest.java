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

/** Checks the member-write sites and the constructor sites of TypeScript and JavaScript. */
class NodeMemberWriteSitesTest {

  private static final String WRITES =
      """
      function go(el, html) {
        el.innerHTML = sanitize(html);
        el['outerHTML'] = html;
        el.title += html;
        if (el.a == html) { el.b; }
      }
      """;

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
        sitesOf(NodeTaintFixtures.graph(language, WRITES, "go")).stream()
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
        sitesOf(NodeTaintFixtures.graph(language, WRITES, "go")).stream()
            .filter(site -> site.kind() == FlowSiteKind.MEMBER_WRITE && "a".equals(site.name()))
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
    final FlowSite init =
        siteOn(NodeTaintFixtures.graph(language, source, "go"), "<init>", "Function");
    assertThat(init.qualifier()).isEqualTo("Function");
    assertThat(init.receiverType()).isEmpty();
    assertThat(init.arguments()).hasSize(1);
  }

  private static FlowSite writeOn(final AnalysisLanguage language, final String name) {
    return sitesOf(NodeTaintFixtures.graph(language, WRITES, "go")).stream()
        .filter(site -> site.kind() == FlowSiteKind.MEMBER_WRITE && site.name().equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "no write of "
                        + name
                        + "\n"
                        + ControlFlowDump.render(NodeTaintFixtures.graph(language, WRITES, "go"))));
  }

  private static FlowSite siteOn(
      final ControlFlowGraph graph, final String name, final String qualifier) {
    return sitesOf(graph).stream()
        .filter(site -> site.name().equals(name) && site.qualifier().equals(qualifier))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("no site " + name + "\n" + ControlFlowDump.render(graph)));
  }

  private static List<FlowSite> sitesOf(final ControlFlowGraph graph) {
    return graph.blocks().stream().map(BasicBlock::sites).flatMap(List::stream).toList();
  }

  private static String argumentText(final FlowSite site, final String source) {
    final Span span = site.arguments().get(0);
    return source.substring(span.startOffset(), span.endOffset());
  }
}
