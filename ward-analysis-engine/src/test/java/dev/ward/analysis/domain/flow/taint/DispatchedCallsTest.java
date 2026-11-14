package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DispatchedCallsTest {
  private static final Span SPAN = new Span(1, 1, 1, 10, 0, 9);
  private static final Set<String> KNOWN = Set.of("Impl#go/1");
  private static final TaintSpec EMPTY = new TaintSpec(List.of(), List.of(), List.of(), List.of());

  private static FlowSite call(
      final String qualifier, final Optional<String> type, final List<String> annotations) {
    return new FlowSite(FlowSiteKind.CALL, "go", qualifier, type, annotations, List.of(SPAN), SPAN);
  }

  private static String qualifierAfter(final FlowSite site, final TaintSpec spec) {
    final BasicBlock block = new BasicBlock(0, List.of(), List.of(), List.of(), List.of(site));
    final ControlFlowGraph graph =
        new ControlFlowGraph("A#m(0)", SPAN, List.of(block, exit()), 0, 1);
    return DispatchedCalls.apply(graph, KNOWN, spec).blocks().get(0).sites().get(0).qualifier();
  }

  private static BasicBlock exit() {
    return new BasicBlock(1, List.of(), List.of(), List.of(), List.of());
  }

  @Test
  void aCreationTypeWinsOverTheDeclaredType() {
    final FlowSite site =
        call("b", Optional.of("shop.Base"), List.of(DispatchedCalls.CREATED_AS + "shop.Impl"));
    assertThat(qualifierAfter(site, EMPTY)).isEqualTo("Impl");
  }

  @Test
  void aDeclaredClassWithABodyIsFollowed() {
    assertThat(qualifierAfter(call("i", Optional.of("shop.Impl"), List.of()), EMPTY))
        .isEqualTo("Impl");
  }

  @Test
  void aClassWithoutABodyForTheMethodIsLeftAlone() {
    assertThat(qualifierAfter(call("b", Optional.of("shop.Base"), List.of()), EMPTY))
        .isEqualTo("b");
    assertThat(qualifierAfter(call("", Optional.of("shop.Impl"), List.of()), EMPTY)).isEmpty();
  }

  @Test
  void aCallThatTheSpecificationMatchesKeepsItsQualifier() {
    final TaintSpec sink =
        new TaintSpec(
            List.of(),
            List.of(
                new TaintEntry(
                    FlowSiteKind.CALL, "go", Optional.empty(), Optional.of("i"), List.of(0))),
            List.of(),
            List.of());
    assertThat(qualifierAfter(call("i", Optional.of("shop.Impl"), List.of()), sink)).isEqualTo("i");
  }
}
