package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports a derived class constructor that reaches its end, or reads {@code this}, on a code path
 * that has not called {@code super()}. The finding sits on the constructor. A path that leaves the
 * body by an exception is not a return and is left alone. A second super() call on one path is
 * reported as well.
 */
public final class BiomeUnreachableSuperCheck implements RuleCheck {

  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String METHOD = "methodDefinition";
  private static final String CLASS_TAIL = "classTail";
  private static final String NAME = "classElementName";
  private static final String EXTENDS_TOKEN = "'extends'";
  private static final String CLASS = "classDeclaration";
  private static final String HERITAGE = "classHeritage";
  private static final String EXTENDS = "classExtendsClause";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeUnreachableSuperCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the control-flow graphs, which only {@link #matches(SyntaxNode,
   * FlowView)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unreachable-super check needs the control-flow graphs");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final Map<Span, ControlFlowGraph> bySpan = new HashMap<>();
    flow.graphs().forEach(graph -> bySpan.put(graph.span(), graph));
    return SyntaxWalk.visits(root, Set.of(CONSTRUCTOR, METHOD)).stream()
        .filter(visit -> isConstructor(visit.node()))
        .filter(visit -> derived(visit.ancestors()))
        .filter(visit -> bySpan.containsKey(visit.node().span()))
        .filter(visit -> SuperCallFlow.misses(bySpan.get(visit.node().span())))
        .map(visit -> new RuleMatch(visit.node().span(), message))
        .sorted(Comparator.comparingInt(match -> match.span().startOffset()))
        .toList();
  }

  /** A method that is a constructor: a constructor declaration, or a method named constructor. */
  private static boolean isConstructor(final SyntaxNode node) {
    return CONSTRUCTOR.equals(node.kind())
        || METHOD.equals(node.kind())
            && node.children().stream()
                .filter(child -> NAME.equals(child.kind()))
                .anyMatch(name -> "constructor".equals(SyntaxWalk.text(name)));
  }

  /** The nearest enclosing class lists an {@code extends} clause, in either grammar's shape. */
  private static boolean derived(final List<SyntaxNode> ancestors) {
    for (int level = ancestors.size() - 1; level >= 0; level--) {
      final SyntaxNode node = ancestors.get(level);
      if (CLASS.equals(node.kind())) {
        return node.children().stream().anyMatch(BiomeUnreachableSuperCheck::extendsClause);
      }
    }
    return false;
  }

  private static boolean extendsClause(final SyntaxNode part) {
    return HERITAGE.equals(part.kind()) && BiomeTree.hasChild(part, EXTENDS)
        || CLASS_TAIL.equals(part.kind()) && BiomeTree.hasChild(part, EXTENDS_TOKEN);
  }
}
