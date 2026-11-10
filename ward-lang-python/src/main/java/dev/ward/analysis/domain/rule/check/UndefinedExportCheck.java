package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports each name in a module's {@code __all__} list that the module does not bind, like pyflakes
 * {@code F822}. A module with a star import is left alone, since the import may bind the name.
 */
public final class UndefinedExportCheck implements RuleCheck {

  private static final String ASSIGNMENT = "__all__=";
  private static final String NAME = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public UndefinedExportCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the undefined-export check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(Set.of());
    if (scopes.module().open()) {
      return List.of();
    }
    final Set<String> bound = names(scopes.module().bindings());
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode export : exports(root)) {
      final String name = SyntaxWalk.text(export).replaceAll("^[\"']|[\"']$", "");
      if (!bound.contains(name)) {
        found.add(new RuleMatch(export.span(), message.replace(NAME, name)));
      }
    }
    return found;
  }

  private static Set<String> names(final List<Binding> bindings) {
    return bindings.stream().map(Binding::name).collect(Collectors.toUnmodifiableSet());
  }

  /** The string literals of each module-level assignment to {@code __all__}. */
  private static List<SyntaxNode> exports(final SyntaxNode root) {
    final List<SyntaxNode> strings = new ArrayList<>();
    for (final SyntaxNode statement : root.children()) {
      if (isExportAssignment(statement)) {
        SyntaxWalk.leaves(statement).stream()
            .filter(leaf -> SyntaxWalk.text(leaf).matches("[\"'].*[\"']"))
            .forEach(strings::add);
      }
    }
    return strings;
  }

  private static boolean isExportAssignment(final SyntaxNode statement) {
    return SyntaxWalk.descendants(statement).stream()
        .anyMatch(
            node ->
                "expr_stmt".equals(node.kind()) && SyntaxWalk.text(node).startsWith(ASSIGNMENT));
  }
}
