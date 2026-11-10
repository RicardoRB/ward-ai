package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reports a name that a scope declares more than once, like ESLint {@code no-redeclare}: each
 * declaration after the first is reported at its name. A parameter and a {@code var} of the same
 * name in one function count as two declarations. The builtin-globals option is not offered.
 */
public final class NodeRedeclareCheck implements RuleCheck {

  private static final int FIRST_DECLARATION = 1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding, with {@code {value}} as the name
   */
  public NodeRedeclareCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the redeclare check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      redeclarations(scope.bindings(), matches);
    }
    return List.copyOf(matches);
  }

  /**
   * The bindings of a scope grouped by name; every binding after the first of a name is reported.
   */
  private void redeclarations(final List<Binding> bindings, final List<RuleMatch> matches) {
    final Map<String, Integer> seen = new LinkedHashMap<>();
    for (final Binding binding : bindings) {
      final int count = seen.merge(binding.name(), 1, Integer::sum);
      if (count > FIRST_DECLARATION) {
        matches.add(
            new RuleMatch(binding.declaration(), message.replace("{value}", binding.name())));
      }
    }
  }
}
