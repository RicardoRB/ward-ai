package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the flow sites that the TypeScript and JavaScript lowerings record on each block. */
class NodeFlowSitesTest {

  private static final String POOL =
      """
      import { Pool } from 'pg';

      function run(text: string, unknown) {
        const db = new Pool();
        const req = input();
        db.query(req.query);
        unknown.query(text);
      }
      """;

  private static final String HANDLER =
      """
      class Handler {
        handle(@Untrusted input: string) {
          sink(input);
        }
      }
      """;

  /** Scenario: Unknown receiver. The name is declared nowhere, so it has no receiver type. */
  @Test
  void unknownReceiverHasNoReceiverType() {
    final FlowSite call = siteOn(graphOf(POOL, "run"), FlowSiteKind.CALL, "query", "unknown");
    assertThat(call.receiverType()).isEmpty();
  }

  /** A receiver declared with an imported {@code new} expression carries the module type. */
  @Test
  void importedNewReceiverCarriesModuleType() {
    final FlowSite call = siteOn(graphOf(POOL, "run"), FlowSiteKind.CALL, "query", "db");
    assertThat(call.receiverType()).contains("pg.Pool");
    assertThat(call.arguments()).hasSize(1);
  }

  /** Scenario: Member read. The property is listed with its qualifier. */
  @Test
  void memberReadListsPropertyAndQualifier() {
    final FlowSite read = siteOn(graphOf(POOL, "run"), FlowSiteKind.MEMBER_READ, "query", "req");
    assertThat(read.qualifier()).isEqualTo("req");
  }

  /**
   * Scenario: Annotated parameter. A decorated parameter is a parameter site with its decorator.
   */
  @Test
  void decoratedParameterIsASite() {
    final FlowSite parameter =
        siteOn(graphOf(HANDLER, "handle"), FlowSiteKind.PARAMETER, "input", "");
    assertThat(parameter.annotations()).containsExactly("Untrusted");
  }

  /** Inner calls come before the calls that contain them. */
  @Test
  void innerCallComesBeforeOuterCall() {
    final List<String> calls =
        sites(graphOf(POOL, "run"), FlowSiteKind.CALL).stream().map(FlowSite::name).toList();
    assertThat(calls).containsSubsequence("input", "query");
  }

  private static ControlFlowGraph graphOf(final String source, final String prefix) {
    return NodeFlowFixtures.lower(source).graphs().stream()
        .filter(graph -> graph.body().startsWith(prefix))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no graph for " + prefix));
  }

  private static FlowSite siteOn(
      final ControlFlowGraph graph,
      final FlowSiteKind kind,
      final String name,
      final String qualifier) {
    return sites(graph, kind).stream()
        .filter(site -> site.name().equals(name) && site.qualifier().equals(qualifier))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no site " + name + " on " + qualifier));
  }

  private static List<FlowSite> sites(final ControlFlowGraph graph, final FlowSiteKind kind) {
    return graph.blocks().stream()
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .filter(site -> site.kind() == kind)
        .toList();
  }
}
