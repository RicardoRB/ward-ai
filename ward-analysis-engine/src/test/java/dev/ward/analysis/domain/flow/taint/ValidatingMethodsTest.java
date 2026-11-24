package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which methods validate which parameters, from the marks of their returns. */
class ValidatingMethodsTest {
  private static final String HOST = "host";
  private static final int ARITY = 3;
  private static final Span DECLARATION = span(0, 1);
  private static final Span CALL = span(10, 14);
  private static final Span ARGUMENT = span(15, 16);
  private static final Span RETURN = span(5, 20);
  private static final int ENTRY = 0;
  private static final int EXIT = 1;

  private static Span span(final int start, final int end) {
    return new Span(1, start + 1, 1, end + 1, start, end);
  }

  private static FlowSite mark(final String parameter, final String how, final Span... pairs) {
    return new FlowSite(
        FlowSiteKind.CALL,
        ValidatingMethods.RETURN,
        parameter,
        Optional.empty(),
        List.of(HOST, how),
        List.of(pairs),
        RETURN
    );
  }

  private static LocalMethod method(final String name, final List<FlowSite> sites) {
    final BasicBlock entry =
        new BasicBlock(
            ENTRY,
            List.of(),
            List.of(),
            List.of(new VariableAccess("x", AccessKind.PARAMETER, DECLARATION, DECLARATION)),
            sites
        );
    final BasicBlock exit = new BasicBlock(EXIT, List.of(), List.of(), List.of(), List.of());
    final ControlFlowGraph graph =
        new ControlFlowGraph(name, span(0, 30), List.of(entry, exit), ENTRY, EXIT);
    return new LocalMethod(name.substring(name.indexOf('#') + 1), "m", 1, graph, false);
  }

  private static FlowSite callOf(final String name) {
    return new FlowSite(
        FlowSiteKind.CALL,
        name,
        "",
        Optional.empty(),
        List.of(),
        List.of(ARGUMENT),
        CALL
    );
  }

  @Test
  void aMethodWhoseReturnsAreAllMarkedSafeValidatesTheParameter() {
    final LocalMethod method = method("m#check", List.of(mark("x", "safe")));

    assertThat(ValidatingMethods.parameters(method, HOST, Map.of(), Map.of())).containsExactly(0);
  }

  @Test
  void aMethodWithoutMarksOrForAnotherKindValidatesNothing() {
    final LocalMethod none = method("m#plain", List.of());
    final LocalMethod other = method("m#other", List.of(mark("y", "safe")));

    assertThat(ValidatingMethods.parameters(none, HOST, Map.of(), Map.of())).isEmpty();
    assertThat(ValidatingMethods.parameters(other, HOST, Map.of(), Map.of())).isEmpty();
    assertThat(ValidatingMethods.parameters(none, "other", Map.of(), Map.of())).isEmpty();
  }

  @Test
  void aReturnThatCallsAValidatorIsProvedOnlyWhenTheCalleeValidatesThatArgument() {
    final LocalMethod outer =
        method("m#outer", List.of(callOf("inner"), mark("x", "calls", CALL, ARGUMENT)));
    final LocalMethod inner = method("m#inner", List.of(mark("x", "safe")));
    final Map<String, LocalMethod> methods = Map.of("inner/1", inner);

    assertThat(ValidatingMethods.parameters(outer, HOST, methods, Map.of("inner/1", Set.of(0))))
        .containsExactly(0);
    assertThat(ValidatingMethods.parameters(outer, HOST, methods, Map.of())).isEmpty();
    assertThat(ValidatingMethods.parameters(outer, HOST, methods, Map.of("inner/1", Set.of(1))))
        .isEmpty();
  }

  @Test
  void theNameOfAUseCarriesTheFunctionAndTheArity() {
    final String name = ValidatedUses.name(HOST, "check", 1);
    final FlowSite use =
        new FlowSite(
            FlowSiteKind.VALIDATED,
            name,
            "",
            Optional.empty(),
            List.of("ward:arity:" + ARITY),
            List.of(ARGUMENT),
            ARGUMENT
        );

    assertThat(name).isEqualTo("host:check@1");
    assertThat(ValidatedUses.function(use)).isEqualTo("check");
    assertThat(ValidatedUses.arity(use)).isEqualTo(ARITY);
  }

  @Test
  void aSpecificationTrustsTheKindsItNamesAndSummariesFindThem() {
    final TaintEntry kind =
        new TaintEntry(FlowSiteKind.VALIDATED, HOST, Optional.empty(), Optional.empty(), List.of());
    final TaintSpec spec = new TaintSpec(List.of(), List.of(), List.of(kind), List.of());
    final LocalMethod method = method("m#check", List.of(mark("x", "safe")));

    assertThat(ValidatingMethods.of(method, spec, Map.of(), Map.of()))
        .containsEntry(HOST, Set.of(0));
    assertThat(ValidatedUses.use(method, "m", HOST, 0).name()).isEqualTo("host:check@0");
  }
}
