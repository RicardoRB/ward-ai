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
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each function, class or import that rebinds a name of an earlier function, class or
 * import in the same scope before anything read the earlier one, like pyflakes {@code F811}.
 */
public final class RedefinitionCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final Set<BindingKind> DEFINITIONS =
      Set.of(BindingKind.FUNCTION, BindingKind.CLASS, BindingKind.IMPORT);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public RedefinitionCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the redefinition check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (!scope.open()) {
        addRedefinitions(scope.bindings(), scopes, found);
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private void addRedefinitions(
      final List<Binding> bindings, final UnitScopes scopes, final List<RuleMatch> found) {
    for (int index = 0; index < bindings.size(); index++) {
      final Binding current = bindings.get(index);
      if (DEFINITIONS.contains(current.kind())) {
        final Optional<Binding> previous = previousDefinition(bindings.subList(0, index), current);
        if (previous.isPresent() && unusedBetween(previous.get(), current, scopes)) {
          found.add(new RuleMatch(current.declaration(), message.replace(NAME, current.name())));
        }
      }
    }
  }

  private static Optional<Binding> previousDefinition(
      final List<Binding> earlier, final Binding current) {
    return earlier.stream()
        .filter(binding -> binding.name().equals(current.name()))
        .filter(binding -> DEFINITIONS.contains(binding.kind()))
        .reduce((first, second) -> second);
  }

  private static boolean unusedBetween(
      final Binding previous, final Binding current, final UnitScopes scopes) {
    return scopes.usesOf(previous).stream()
        .noneMatch(
            use ->
                use.span().startOffset() >= previous.declaration().endOffset()
                    && use.span().startOffset() < current.declaration().startOffset());
  }
}
