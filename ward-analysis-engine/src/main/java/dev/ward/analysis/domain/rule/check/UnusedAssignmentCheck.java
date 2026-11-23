package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.DefUseChains;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports each write of a local variable or parameter whose value no read can observe, with PMD's
 * {@code UnusedAssignment} rules. A write is reported when a later write can replace it, so an
 * initializer or incoming parameter value that nothing overwrites is left alone. A plain assignment
 * is reported unless its name is exempt, which starts with {@code _}, {@code ignored} or
 * {@code unused}. A variable that a nested body captures is never reported.
 */
public final class UnusedAssignmentCheck implements RuleCheck {
  private static final String FINALLY_BLOCK = "finallyBlock";
  private static final String NAME = "{value}";
  private static final String UNDERSCORE = "_";
  private static final String IGNORED = "ignored";
  private static final String UNUSED = "unused";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the variable name
   */
  public UnusedAssignmentCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: this check reads the control-flow graphs of the unit, which only {@link
   * #matches(SyntaxNode, FlowView)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unused-assignment check needs the data-flow graphs");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final ControlFlowGraph graph : flow.graphs()) {
      found.addAll(unused(graph, finallySpans(root)));
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private List<RuleMatch> unused(final ControlFlowGraph graph, final List<Span> finallies) {
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);
    final DefUseChains chains = DefUseChains.of(reaching);
    final Set<Span> captured = captured(graph);
    final Set<Span> readInFinally = readInFinally(graph, finallies);
    final List<RuleMatch> found = new ArrayList<>();
    for (final AccessSite definition : chains.definitions()) {
      final VariableAccess access = definition.access();
      if (chains.usesOf(definition).isEmpty()
          && !captured.contains(access.declaration())
          && !(readInFinally.contains(access.declaration()) && precedesFinally(access, finallies))
          && reportable(definition, reaching)) {
        found.add(new RuleMatch(access.span(), message.replace(NAME, access.name())));
      }
    }
    return found;
  }

  /**
   * Reports whether an unread write is a finding. An initializer and a parameter's incoming value
   * are findings only when a later write replaces them. A plain assignment is also a finding when
   * its name is not exempt.
   */
  private static boolean reportable(
      final AccessSite definition,
      final ReachingDefinitions reaching
  ) {
    final VariableAccess access = definition.access();
    return reaching.overwritten(definition) || isAssignment(access) && !exempt(access.name());
  }

  /** Reports whether a write is a plain assignment, rather than an initializer or a parameter. */
  private static boolean isAssignment(final VariableAccess access) {
    return access.kind() == AccessKind.WRITE && !access.span().equals(access.declaration());
  }

  /** Returns the spans of the finally blocks, which any statement of their try can reach. */
  private static List<Span> finallySpans(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(FINALLY_BLOCK)).stream()
        .map(visit -> visit.node().span())
        .toList();
  }

  /**
   * Returns where each variable that a finally block reads is declared. The graph has no edge from
   * the start of a try body to the finally block, so such a read cannot see an earlier value.
   */
  private static Set<Span> readInFinally(final ControlFlowGraph graph, final List<Span> finallies) {
    final Set<Span> read = new HashSet<>();
    for (final BasicBlock block : graph.blocks()) {
      for (final VariableAccess access : block.accesses()) {
        if (access.kind() == AccessKind.READ && inAny(access.span(), finallies)) {
          read.add(access.declaration());
        }
      }
    }
    return read;
  }

  private static boolean inAny(final Span span, final List<Span> finallies) {
    return finallies.stream()
        .anyMatch(f -> f.startOffset() <= span.startOffset() && span.endOffset() <= f.endOffset());
  }

  private static boolean precedesFinally(final VariableAccess access, final List<Span> finallies) {
    return finallies.stream().anyMatch(f -> access.span().endOffset() <= f.startOffset());
  }

  /** Returns where each variable that a nested body captures is declared. */
  private static Set<Span> captured(final ControlFlowGraph graph) {
    final Set<Span> captured = new HashSet<>();
    for (final BasicBlock block : graph.blocks()) {
      for (final VariableAccess access : block.accesses()) {
        if (access.kind() == AccessKind.CAPTURE) {
          captured.add(access.declaration());
        }
      }
    }
    return captured;
  }

  private static boolean exempt(final String name) {
    return name.startsWith(UNDERSCORE) || name.startsWith(IGNORED) || name.startsWith(UNUSED);
  }
}
