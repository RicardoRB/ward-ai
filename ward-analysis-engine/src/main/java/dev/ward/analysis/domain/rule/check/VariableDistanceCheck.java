package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each variable whose first use comes more than {@code max} statements after its
 * declaration, like Checkstyle's {@code VariableDeclarationUsageDistance}. Constants are final and
 * so are left alone, as the standard preset's {@code ignoreFinal} does. The distance counts the
 * sibling nodes between the declaration and the child that holds the first use.
 */
public final class VariableDistanceCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final String COUNT = "{max}";
  private static final Set<ScopeKind> LOCAL_SCOPES = Set.of(ScopeKind.FUNCTION, ScopeKind.BLOCK);
  private static final Set<String> WRAPPERS =
      Set.of("statements", "statements_impl", "stmt", "statementList");

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the most statements allowed between a declaration and its first use
   * @param message the message; {@code {value}} is the name and {@code {max}} the limit
   */
  public VariableDistanceCheck(final int max, final String message) {
    this.max = max;
    this.message = message;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the variable distance check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (LOCAL_SCOPES.contains(scope.kind())) {
        scope.bindings().stream()
            .filter(VariableDistanceCheck::isVariable)
            .filter(binding -> tooFar(root, binding, scopes))
            .forEach(binding -> found.add(match(binding)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private static boolean isVariable(final Binding binding) {
    return binding.kind() == BindingKind.VARIABLE;
  }

  private boolean tooFar(final SyntaxNode root, final Binding binding, final UnitScopes scopes) {
    final Optional<Span> first = firstUse(binding, scopes);
    return first.isPresent() && distance(root, binding.declaration(), first.get()) > max;
  }

  private static Optional<Span> firstUse(final Binding binding, final UnitScopes scopes) {
    return scopes.usesOf(binding).stream()
        .filter(use -> use.access() != ReferenceAccess.WRITE)
        .map(NameReference::span)
        .min(Comparator.comparingInt(Span::startOffset));
  }

  private RuleMatch match(final Binding binding) {
    return new RuleMatch(
        binding.declaration(),
        message.replace(NAME, binding.name()).replace(COUNT, String.valueOf(max)));
  }

  /**
   * Returns the number of statements strictly between the declaration and the use, counted among
   * the items of the lowest node that holds both, or -1 when the use does not follow the
   * declaration there.
   */
  private static int distance(final SyntaxNode root, final Span declaration, final Span use) {
    SyntaxNode node = root;
    while (true) {
      final List<SyntaxNode> items = items(node);
      final int from = indexHolding(items, declaration);
      final int to = indexHolding(items, use);
      if (from < 0 || to < 0) {
        return -1;
      }
      if (from != to) {
        return to > from ? to - from - 1 : -1;
      }
      node = items.get(from);
    }
  }

  /**
   * The children of a node, with the statement-list wrappers replaced by the statements they hold.
   */
  private static List<SyntaxNode> items(final SyntaxNode node) {
    final List<SyntaxNode> items = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (WRAPPERS.contains(child.kind())) {
        items.addAll(items(child));
      } else {
        items.add(child);
      }
    }
    return items;
  }

  private static int indexHolding(final List<SyntaxNode> items, final Span span) {
    for (int index = 0; index < items.size(); index++) {
      final Span child = items.get(index).span();
      if (child.startOffset() <= span.startOffset() && span.startOffset() < child.endOffset()) {
        return index;
      }
    }
    return -1;
  }
}
