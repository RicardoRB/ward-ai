package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each parameter or local variable that has the name of a property of its enclosing type,
 * like Checkstyle's {@code HiddenField}. The parameters of an initializer are left alone, as the
 * standard preset's {@code ignoreConstructorParameter} does.
 */
public final class HiddenFieldCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final Pattern INITIALIZER = Pattern.compile("init[?!]?\\(");
  private static final Set<ScopeKind> LOCAL_SCOPES = Set.of(ScopeKind.FUNCTION, ScopeKind.BLOCK);
  private static final Set<BindingKind> SHADOWING_KINDS =
      Set.of(BindingKind.VARIABLE, BindingKind.CONSTANT);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public HiddenFieldCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the hidden-field check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (LOCAL_SCOPES.contains(scope.kind())) {
        scope.bindings().stream()
            .filter(binding -> hides(binding, scope, scopes, unit))
            .forEach(binding -> found.add(match(binding)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private RuleMatch match(final Binding binding) {
    return new RuleMatch(binding.declaration(), message.replace(NAME, binding.name()));
  }

  private static boolean hides(
      final Binding binding, final Scope scope, final UnitScopes scopes, final SourceUnit unit) {
    final boolean local =
        binding.kind() == BindingKind.VARIABLE
            || binding.kind() == BindingKind.CONSTANT
            || binding.kind() == BindingKind.PARAMETER;
    return local
        && !(binding.kind() == BindingKind.PARAMETER && isInitializer(scope, unit))
        && enclosingMember(scope, binding.name(), scopes).isPresent();
  }

  private static Optional<Binding> enclosingMember(
      final Scope scope, final String name, final UnitScopes scopes) {
    Optional<Scope> ancestor = scopes.parent(scope);
    while (ancestor.isPresent() && ancestor.get().kind() != ScopeKind.CLASS) {
      ancestor = scopes.parent(ancestor.get());
    }
    return ancestor.flatMap(
        type ->
            type.bindings().stream()
                .filter(member -> SHADOWING_KINDS.contains(member.kind()))
                .filter(member -> member.name().equals(name))
                .findFirst());
  }

  private static boolean isInitializer(final Scope scope, final SourceUnit unit) {
    final String text = unit.text();
    final int start = scope.span().startOffset();
    return scope.kind() == ScopeKind.FUNCTION
        && start <= text.length()
        && INITIALIZER.matcher(text).region(start, text.length()).lookingAt();
  }
}
