package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lowers the receiver of a call that has a lambda, before the lambda: what the receiver reads and
 * calls, a variable for the receiver's value, and the lambda's parameters, which read that
 * variable.
 */
final class KotlinReceiverLowering {

  private final ControlFlowBuilder flow;
  private final KotlinView view;

  KotlinReceiverLowering(final ControlFlowBuilder flow, final KotlinView view) {
    this.flow = flow;
    this.view = view;
  }

  private void fresh() {
    final int next = flow.newBlock();
    flow.edge(flow.current(), next, FlowEdgeKind.NORMAL);
    flow.moveTo(next);
  }

  static KotlinResult result(final KotlinSplits.Split split) {
    return KotlinResult.of("receiver", split.receiver());
  }

  /**
   * The receiver comes first. It writes the lambda's parameters, so what the receiver reads reaches
   * them, and a variable for the receiver itself.
   */
  void lower(
      final SyntaxNode statement,
      final KotlinSplits.Split split,
      final List<KotlinSplits.Split> all) {
    final Span region = split.receiver();
    fresh();
    final List<Span> hide = new ArrayList<>();
    for (final KotlinSplits.Split other : all) {
      hide.add(other.span());
      if (other.receiver() != null
          && !other.receiver().equals(region)
          && KotlinView.contains(region, other.receiver())) {
        hide.add(other.receiver());
      }
    }
    final SyntaxNode part =
        new SyntaxNode("receiver", KotlinResult.widened(region), Map.of(), List.of(statement));
    view.narrow(hide, region, true, false);
    flow.add(part, false);
    view.reset();
    final KotlinResult receiver = result(split);
    final List<VariableAccess> found = new ArrayList<>(earlierValues(split, all));
    found.add(receiver.write(part.span()));
    flow.record(found, List.of());
    parameters(split, receiver, part);
  }

  /**
   * The parameters read the receiver's variable in a block of their own. The engine taints the last
   * write before a source of the receiver, which is the receiver's variable, so the parameters must
   * take their taint from that variable, not from the source.
   */
  private void parameters(
      final KotlinSplits.Split split, final KotlinResult receiver, final SyntaxNode part) {
    final int next = flow.newBlock();
    flow.edge(flow.current(), next, FlowEdgeKind.NORMAL);
    flow.moveTo(next);
    view.showNothing();
    flow.add(new SyntaxNode("parameters", part.span(), Map.of(), List.of()), false);
    view.reset();
    final List<VariableAccess> found = new ArrayList<>();
    found.add(receiver.read(part.span()));
    found.addAll(KotlinLambdaParameters.writes(split, part.span()));
    flow.record(found, List.of());
  }

  /**
   * The values of the lambdas written inside this lambda's receiver, as in {@code a.let{}.map{}}.
   */
  private List<VariableAccess> earlierValues(
      final KotlinSplits.Split split, final List<KotlinSplits.Split> all) {
    final List<VariableAccess> reads = new ArrayList<>();
    for (final KotlinSplits.Split earlier : all) {
      if (!earlier.equals(split) && KotlinView.contains(split.receiver(), earlier.span())) {
        reads.add(KotlinResult.of("value", earlier.span()).read(earlier.span()));
      }
    }
    return reads;
  }
}
