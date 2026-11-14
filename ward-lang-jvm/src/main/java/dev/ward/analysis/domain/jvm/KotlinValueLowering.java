package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers an expression statement that holds lambdas. The statement is cut into parts that run in
 * this order: the receiver of each lambda's call, the lambda's own statements, and the rest of the
 * statement. Everything runs once, and a lambda may be skipped, so the block before a lambda also
 * reaches the block after it.
 *
 * <p>The lambda parameter, or {@code it}, is written from what the receiver reads, so a tainted
 * receiver taints it. The lambda's last expression is the lambda's value; the call's value is that
 * value for {@code let}, {@code run}, {@code with}, {@code use} and {@code synchronized}, the
 * receiver for {@code also}, {@code apply}, {@code takeIf} and {@code takeUnless}, and both for any
 * other call, so a sanitizer that is the last expression of a {@code let} cleans its result.
 *
 * <p>shortcut: a lambda runs where it is written, once, even when it is stored and called later;
 * upgrade with a call graph when a rule needs the later call.
 */
final class KotlinValueLowering {

  private static final Set<String> LAMBDA_VALUE =
      Set.of("let", "run", "with", "use", "synchronized");
  private static final Set<String> RECEIVER_VALUE = Set.of("also", "apply", "takeIf", "takeUnless");

  private final ControlFlowBuilder flow;
  private final KotlinView view;
  private final KotlinStatements statements;
  private final Deque<Frame> frames = new ArrayDeque<>();
  private final KotlinReceiverLowering receivers;

  /** A lambda being lowered: the names a labelled return can use, and where it leaves to. */
  private record Frame(String callee, String label, KotlinResult value, int after) {}

  KotlinValueLowering(
      final ControlFlowBuilder flow, final KotlinView view, final KotlinStatements statements) {
    this.flow = flow;
    this.view = view;
    this.statements = statements;
    this.receivers = new KotlinReceiverLowering(flow, view);
  }

  /**
   * Lowers an expression into the current block, cutting out its lambdas.
   *
   * @param node the statement or expression
   * @param mayThrow whether it contains a call, a throw or a non-null assertion
   * @param result where its value goes, or null when the value is not used
   */
  void expression(final SyntaxNode node, final boolean mayThrow, final KotlinResult result) {
    final List<KotlinSplits.Split> splits = KotlinSplits.of(node);
    final SyntaxNode shown = result == null ? node : valueNode(node);
    if (splits.isEmpty()) {
      freshBlock(result != null);
      flow.add(shown, mayThrow);
      if (result != null) {
        flow.record(List.of(result.write(shown.span())), List.of());
      }
      return;
    }
    final List<VariableAccess> reads = new ArrayList<>();
    for (final KotlinSplits.Split split : splits) {
      final List<VariableAccess> own = lowerSplit(node, split, splits);
      if (!isReceivedByAnother(split, splits)) {
        reads.addAll(own);
      }
    }
    freshBlock(result != null);
    view.narrow(hidden(splits), null, true, true);
    flow.add(shown, mayThrow);
    view.reset();
    if (result != null) {
      reads.add(result.write(shown.span()));
    }
    flow.record(reads, List.of());
  }

  /**
   * A node whose span is widened, to let a write start before it, goes in a block of its own, so
   * the node before it cannot claim the write.
   */
  private void freshBlock(final boolean needed) {
    if (needed) {
      final int next = flow.newBlock();
      flow.edge(flow.current(), next, FlowEdgeKind.NORMAL);
      flow.moveTo(next);
    }
  }

  /**
   * Leaves the innermost lambda that a label names, with the value of the jump.
   *
   * @param label the name after {@code return@}
   * @param value the returned expression, or null
   * @return whether a lambda of that label is being lowered
   */
  boolean leaveLambda(final String label, final SyntaxNode value) {
    for (final Frame frame : frames) {
      if (label.equals(frame.label()) || label.equals(frame.callee())) {
        if (value != null) {
          expression(value, KotlinFlowSyntax.mayThrow(value), frame.value());
        }
        flow.edge(flow.current(), frame.after(), FlowEdgeKind.NORMAL);
        flow.moveTo(flow.newBlock());
        return true;
      }
    }
    return false;
  }

  /** A lambda written inside the receiver of another call gives its value to that call. */
  private static boolean isReceivedByAnother(
      final KotlinSplits.Split split, final List<KotlinSplits.Split> all) {
    return all.stream()
        .anyMatch(
            other ->
                !other.equals(split)
                    && other.receiver() != null
                    && KotlinView.contains(other.receiver(), split.span()));
  }

  /** The names that the statement itself reads: not the lambdas, nor the receivers before them. */
  private static List<Span> hidden(final List<KotlinSplits.Split> splits) {
    final List<Span> spans = new ArrayList<>();
    for (final KotlinSplits.Split split : splits) {
      spans.add(split.span());
      if (split.receiver() != null) {
        spans.add(split.receiver());
      }
    }
    return spans;
  }

  private static SyntaxNode valueNode(final SyntaxNode node) {
    return new SyntaxNode("value", KotlinResult.widened(node.span()), Map.of(), List.of(node));
  }

  /** Lowers one lambda with its receiver and returns the reads that stand for its value. */
  private List<VariableAccess> lowerSplit(
      final SyntaxNode statement,
      final KotlinSplits.Split split,
      final List<KotlinSplits.Split> all) {
    final KotlinResult value = KotlinResult.of("value", split.span());
    final List<VariableAccess> reads = new ArrayList<>();
    if (split.receiver() != null) {
      receivers.lower(statement, split, all);
    }
    lambda(split, value);
    if (!RECEIVER_VALUE.contains(split.callee())) {
      reads.add(value.read(split.span()));
    }
    if (split.receiver() != null && !LAMBDA_VALUE.contains(split.callee())) {
      reads.add(KotlinReceiverLowering.result(split).read(split.receiver()));
    }
    return reads;
  }

  /** The lambda's statements, from a block of their own to a block after them. */
  private void lambda(final KotlinSplits.Split split, final KotlinResult value) {
    final SyntaxNode body = KotlinFlowSyntax.child(split.node(), "statements");
    final int inside = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), inside, FlowEdgeKind.NORMAL);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(inside);
    frames.push(new Frame(split.callee(), split.label(), value, after));
    if (body != null) {
      statements.tail(body, value);
    }
    frames.pop();
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }
}
