package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reports a label that has the name of a variable visible where the label is, like ESLint {@code
 * no-label-var}. The variables of the enclosing scopes count, not those of sibling blocks.
 */
public final class NodeLabelVarCheck implements RuleCheck {

  private static final String LABELLED = "labelledStatement";
  private static final String IDENTIFIER = "identifier";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeLabelVarCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the label check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode labelled : labelled(root)) {
      final Optional<SyntaxNode> label = labelOf(labelled);
      if (label.isPresent()
          && declares(scopes, innermost(scopes, labelled.span()), LeafText.of(label.get()))) {
        matches.add(new RuleMatch(labelled.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static List<SyntaxNode> labelled(final SyntaxNode root) {
    final List<SyntaxNode> found = new ArrayList<>();
    collect(root, found);
    return found;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> found) {
    if (LABELLED.equals(node.kind())) {
      found.add(node);
    }
    node.children().forEach(child -> collect(child, found));
  }

  private static Optional<SyntaxNode> labelOf(final SyntaxNode labelled) {
    return labelled.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst();
  }

  /**
   * The innermost scope that holds the span. Scopes are numbered outward first, so the largest id
   * among those holding the span is the innermost, even when a builtin and a module share a span.
   */
  private static Optional<Scope> innermost(final UnitScopes scopes, final Span span) {
    return scopes.scopes().stream()
        .filter(scope -> holds(scope.span(), span))
        .max(Comparator.comparingInt(Scope::id));
  }

  private static boolean holds(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }

  /** Whether a scope or one of its enclosing scopes declares the name. */
  private static boolean declares(
      final UnitScopes scopes, final Optional<Scope> start, final String name) {
    Optional<Scope> current = start;
    while (current.isPresent()) {
      if (current.get().bindings().stream().anyMatch(binding -> name.equals(binding.name()))) {
        return true;
      }
      current = current.flatMap(scopes::parent);
    }
    return false;
  }
}
