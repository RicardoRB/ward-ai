package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a local name that a function annotates without a value and never reads, like pyflakes
 * {@code F842}. A bare annotation binds nothing at run time, so an unread one is dead.
 */
public final class UnusedAnnotationCheck implements RuleCheck {

  private static final Set<String> STATEMENTS = Set.of("expr_stmt");
  private static final String ANNOTATION = "annassign";
  private static final String ASSIGN = "=";
  private static final String NAME = "{value}";
  private static final int PARTS = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public UnusedAnnotationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unused-annotation check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(Set.of());
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, STATEMENTS)) {
      final SyntaxNode statement = visit.node();
      if (annotationOnly(statement)) {
        final Span target = statement.children().get(0).span();
        scopes.bindings().stream()
            .filter(binding -> declaredIn(binding, target) && localUnused(scopes, binding))
            .findFirst()
            .ifPresent(binding -> found.add(match(target, binding)));
      }
    }
    return found;
  }

  private RuleMatch match(final Span target, final Binding binding) {
    return new RuleMatch(target, message.replace(NAME, binding.name()));
  }

  /** Whether the statement is {@code name: annotation} with no assigned value. */
  private static boolean annotationOnly(final SyntaxNode statement) {
    final List<SyntaxNode> parts = statement.children();
    return parts.size() == PARTS
        && ANNOTATION.equals(parts.get(1).kind())
        && SyntaxWalk.leaves(parts.get(1)).stream()
            .noneMatch(leaf -> ASSIGN.equals(SyntaxWalk.text(leaf)));
  }

  private static boolean declaredIn(final Binding binding, final Span target) {
    return target.startOffset() <= binding.declaration().startOffset()
        && binding.declaration().endOffset() <= target.endOffset();
  }

  /** Whether the binding lives in a function and nothing reads it. */
  private static boolean localUnused(final UnitScopes scopes, final Binding binding) {
    final boolean inFunction =
        scopes.scopes().stream()
            .anyMatch(scope -> scope.id() == binding.scope() && scope.kind() == ScopeKind.FUNCTION);
    return inFunction
        && scopes.usesOf(binding).stream().noneMatch(use -> use.access() != ReferenceAccess.WRITE);
  }
}
