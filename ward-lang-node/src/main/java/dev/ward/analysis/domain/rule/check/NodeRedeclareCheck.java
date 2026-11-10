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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a name that a scope declares more than once, like ESLint {@code no-redeclare}: each
 * declaration after the first is reported at its name. A parameter and a {@code var} of the same
 * name in one function count as two declarations. The builtin-globals option is not offered.
 */
public final class NodeRedeclareCheck implements RuleCheck {

  private static final int FIRST_DECLARATION = 1;
  private static final String BUILTIN_NAME = "{value}";

  private final boolean builtinGlobals;
  private final Set<String> environments;
  private final String message;
  private final String builtinMessage;

  /**
   * Creates the check.
   *
   * @param builtinGlobals whether a global declaration of a built-in name is reported
   * @param environments the environments whose built-in names are defined, such as {@code browser}
   * @param message the message of each finding, with {@code {value}} as the name
   * @param builtinMessage the message of a declaration of a built-in name
   */
  public NodeRedeclareCheck(
      final boolean builtinGlobals,
      final Set<String> environments,
      final String message,
      final String builtinMessage) {
    this.builtinGlobals = builtinGlobals;
    this.environments = Set.copyOf(environments);
    this.message = Objects.requireNonNull(message, "message");
    this.builtinMessage = Objects.requireNonNull(builtinMessage, "builtinMessage");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the redeclare check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(environments);
    final Set<String> builtins = builtinNames(scopes);
    final boolean script = !SourceKind.isModule(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (builtinGlobals
          && script
          && !environments.isEmpty()
          && scope.id() == UnitScopes.MODULE_ID) {
        builtinRedeclarations(scope.bindings(), builtins, matches);
      } else {
        redeclarations(scope.bindings(), matches);
      }
    }
    return List.copyOf(matches);
  }

  /** The names the built-in scope declares. */
  private static Set<String> builtinNames(final UnitScopes scopes) {
    final Set<String> names = new HashSet<>();
    for (final Scope scope : scopes.scopes()) {
      if (scope.id() == UnitScopes.BUILTIN_ID) {
        scope.bindings().forEach(binding -> names.add(binding.name()));
      }
    }
    return names;
  }

  /** A global declaration of a built-in name is reported at each of its declarations. */
  private void builtinRedeclarations(
      final List<Binding> bindings, final Set<String> builtins, final List<RuleMatch> matches) {
    for (final Binding binding : bindings) {
      if (builtins.contains(binding.name())) {
        matches.add(
            new RuleMatch(
                binding.declaration(), builtinMessage.replace(BUILTIN_NAME, binding.name())));
      }
    }
    redeclarations(
        bindings.stream().filter(binding -> !builtins.contains(binding.name())).toList(), matches);
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
