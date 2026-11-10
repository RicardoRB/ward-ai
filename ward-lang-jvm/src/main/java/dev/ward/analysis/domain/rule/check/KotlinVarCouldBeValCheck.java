package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each {@code var} that is initialized where it is declared and never assigned again, as
 * detekt's {@code VarCouldBeVal} does. The check covers local variables and private class
 * properties. A write from a lambda counts as an assignment.
 */
public final class KotlinVarCouldBeValCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final String PRIVATE = "private";
  private static final Set<ScopeKind> LOCAL_SCOPES =
      Set.of(ScopeKind.BLOCK, ScopeKind.FUNCTION, ScopeKind.LAMBDA, ScopeKind.COMPREHENSION);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public KotlinVarCouldBeValCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always empty: the check needs the scopes and the text, which only the scoped overload has. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      for (final Binding binding : scope.bindings()) {
        if (candidate(binding, scope, unit.text()) && neverReassigned(binding, scopes)) {
          found.add(new RuleMatch(binding.declaration(), message.replace(NAME, binding.name())));
        }
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return List.copyOf(found);
  }

  private static boolean candidate(final Binding binding, final Scope scope, final String text) {
    final boolean local = LOCAL_SCOPES.contains(scope.kind());
    final boolean privateProperty =
        scope.kind() == ScopeKind.CLASS
            && KotlinDeclarations.hasWord(
                KotlinDeclarations.prefixOnLine(text, binding.declaration()), PRIVATE);
    return binding.kind() == BindingKind.VARIABLE
        && !binding.has(BindingFlag.DESTRUCTURED)
        && (local || privateProperty);
  }

  private static boolean neverReassigned(final Binding binding, final UnitScopes scopes) {
    final List<NameReference> writes =
        scopes.usesOf(binding).stream()
            .filter(use -> use.access() == ReferenceAccess.WRITE)
            .toList();
    final Span declaration = binding.declaration();
    final boolean initialized = writes.stream().anyMatch(write -> write.span().equals(declaration));
    return initialized && writes.stream().allMatch(write -> write.span().equals(declaration));
  }
}
