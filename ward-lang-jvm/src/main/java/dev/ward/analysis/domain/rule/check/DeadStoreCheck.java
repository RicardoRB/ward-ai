package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Narrows the dead stores of {@link UnusedAssignmentCheck} to a kind that SpotBugs reports on its
 * own: a store of the literal {@code null}, or a store to a local variable that has the name of a
 * field of its class. A dead store whose declaration the tree does not show is left out.
 */
public final class DeadStoreCheck implements RuleCheck {

  /** Which dead stores are reported. */
  public enum Kind {
    /** A dead store of {@code null}. */
    NULL,
    /** A dead store to a local variable named like a field of the enclosing class. */
    SHADOWS_FIELD
  }

  private static final String DECLARATOR = "variableDeclarator";
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final int DECLARATOR_PARTS = 3;
  private static final String NULL_LITERAL = "null";

  private final Kind kind;
  private final UnusedAssignmentCheck dead;

  /**
   * Creates the check.
   *
   * @param kind which dead stores to report
   * @param message the message of each finding; {@code {value}} stands for the variable name
   */
  public DeadStoreCheck(final Kind kind, final String message) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.dead = new UnusedAssignmentCheck(Objects.requireNonNull(message, "message"));
  }

  /**
   * Always fails: this check reads the data-flow graphs, which only {@link #matches(SyntaxNode,
   * FlowView)} is given.
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return dead.matches(root);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<Store> stores = stores(root);
    return dead.matches(root, flow).stream()
        .filter(match -> stores.stream().anyMatch(store -> store.at(match) && accepts(store)))
        .toList();
  }

  private boolean accepts(final Store store) {
    return kind == Kind.NULL ? NULL_LITERAL.equals(store.value()) : store.shadowsField();
  }

  /** A write of a variable that the tree shows: where the name starts and what is written. */
  private record Store(int offset, String value, boolean shadowsField) {
    boolean at(final RuleMatch match) {
      return match.span().startOffset() == offset;
    }
  }

  private static List<Store> stores(final SyntaxNode root) {
    final List<Store> stores = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(DECLARATOR, "expression"))) {
      final SyntaxNode node = visit.node();
      if (DECLARATOR.equals(node.kind())) {
        declarator(node, visit.ancestors()).ifPresent(stores::add);
      } else if (Assignments.isAssignment(node)) {
        assignment(node, visit.ancestors()).ifPresent(stores::add);
      }
    }
    return stores;
  }

  private static Optional<Store> declarator(
      final SyntaxNode node, final List<SyntaxNode> ancestors) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() != DECLARATOR_PARTS || !"'='".equals(parts.get(1).kind())) {
      return Optional.empty();
    }
    return Optional.of(
        new Store(
            parts.get(0).span().startOffset(),
            SyntaxWalk.text(parts.get(2)),
            names(ancestors).contains(SyntaxWalk.text(parts.get(0)))));
  }

  private static Optional<Store> assignment(
      final SyntaxNode node, final List<SyntaxNode> ancestors) {
    if (!"'='".equals(node.children().get(1).kind())) {
      return Optional.empty();
    }
    return Optional.of(
        new Store(
            node.children().get(0).span().startOffset(),
            Assignments.value(node),
            names(ancestors).contains(Assignments.target(node))));
  }

  /** Returns the names of the fields of the nearest enclosing class. */
  private static Set<String> names(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (CLASS_DECLARATION.equals(ancestors.get(index).kind())) {
        return ClassMembers.fieldMembers(ancestors.get(index)).stream()
            .flatMap(field -> ClassMembers.fieldNames(field).stream())
            .collect(Collectors.toUnmodifiableSet());
      }
    }
    return Set.of();
  }
}
