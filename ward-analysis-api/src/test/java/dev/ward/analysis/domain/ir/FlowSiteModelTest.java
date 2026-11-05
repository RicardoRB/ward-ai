package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks the flow-site records and the block constructors that keep sites optional. */
class FlowSiteModelTest {

  private static final Span SPAN = new Span(1, 1, 1, 5, 0, 4);

  /** A block built with the older constructors has no flow sites. */
  @Test
  void olderConstructorsLeaveSitesEmpty() {
    assertThat(new BasicBlock(2, List.of(), List.of()).sites()).isEmpty();
    assertThat(new BasicBlock(2, List.of(), List.of(), List.of()).sites()).isEmpty();
  }

  /** The block copies its sites, so later changes to the caller's list do not reach it. */
  @Test
  void blockCopiesItsSites() {
    final List<FlowSite> sites = new ArrayList<>(List.of(site()));
    final BasicBlock block = new BasicBlock(0, List.of(), List.of(), List.of(), sites);
    sites.clear();
    assertThat(block.sites()).hasSize(1);
  }

  /** A site keeps its arguments and annotations, and the receiver type is optional. */
  @Test
  void siteHoldsItsParts() {
    final FlowSite site = site();
    assertThat(site.receiverType()).isEmpty();
    assertThat(site.arguments()).containsExactly(SPAN);
    assertThat(site.annotations()).containsExactly("Untrusted");
  }

  /** A path step needs a span and a message. */
  @Test
  void flowStepNeedsSpanAndMessage() {
    assertThatThrownBy(() -> new FlowStep(null, "m")).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new FlowStep(SPAN, null)).isInstanceOf(NullPointerException.class);
  }

  /** The dump prints each site on its own line after the variable accesses. */
  @Test
  void dumpPrintsSitesOnTheirOwnLines() {
    final BasicBlock block =
        new BasicBlock(
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new FlowSite(
                    FlowSiteKind.CALL,
                    "executeQuery",
                    "st",
                    Optional.of("java.sql.Statement"),
                    List.of(),
                    List.of(SPAN),
                    SPAN)));
    final BasicBlock exit = new BasicBlock(1, List.of(), List.of());
    final ControlFlowGraph graph = new ControlFlowGraph("m", SPAN, List.of(block, exit), 0, 1);
    assertThat(ControlFlowDump.render(graph))
        .contains("\n  s:call executeQuery@st:java.sql.Statement(1)\n");
  }

  private static FlowSite site() {
    return new FlowSite(
        FlowSiteKind.PARAMETER,
        "input",
        "",
        Optional.empty(),
        List.of("Untrusted"),
        List.of(SPAN),
        SPAN);
  }
}
