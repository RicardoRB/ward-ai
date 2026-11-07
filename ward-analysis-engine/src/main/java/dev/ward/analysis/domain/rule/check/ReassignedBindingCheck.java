package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reports each write to a binding of one of the given kinds, after its declaration, such as
 * assigning to a {@code const}, a function, a class or an import. It backs the ESLint rules {@code
 * no-const-assign}, {@code no-func-assign}, {@code no-class-assign} and {@code no-import-assign}.
 * The declaring reference itself is not a write to report.
 */
public final class ReassignedBindingCheck implements RuleCheck {

  private static final String NAME = "{value}";

  private final Set<BindingKind> kinds;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the binding kinds whose writes are reported
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public ReassignedBindingCheck(final Set<BindingKind> kinds, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.message = message;
  }

  /**
   * Creates the check from the names of the binding kinds, such as {@code CONSTANT}.
   *
   * @throws IllegalArgumentException if a name is not a binding kind
   */
  public static ReassignedBindingCheck of(
      final Collection<String> kindNames, final String message) {
    final Set<BindingKind> kinds = EnumSet.noneOf(BindingKind.class);
    for (final String name : kindNames) {
      kinds.add(BindingKind.valueOf(name));
    }
    return new ReassignedBindingCheck(kinds, message);
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the reassigned-binding check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Binding binding : scopes.bindings()) {
      if (kinds.contains(binding.kind())) {
        scopes.usesOf(binding).stream()
            .filter(use -> reassigns(use, binding))
            .forEach(use -> found.add(match(use)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private static boolean reassigns(final NameReference use, final Binding binding) {
    return use.access() == ReferenceAccess.WRITE && !use.span().equals(binding.declaration());
  }

  private RuleMatch match(final NameReference use) {
    return new RuleMatch(use.span(), message.replace(NAME, use.name()));
  }
}
