package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports a use of a function, class or variable that comes textually before its declaration, like
 * ESLint {@code no-use-before-define}. The options choose the kinds to check. Parameters, imports
 * and types are not checked, and the exports and initializer rules of ESLint are not applied.
 */
public final class NodeUseBeforeDefineCheck implements RuleCheck {

  private final Options options;
  private final String message;

  /** The kinds of declaration the rule checks, as ESLint's options name them. */
  public record Options(boolean functions, boolean classes, boolean variables) {}

  /**
   * Creates the check.
   *
   * @param options the kinds to check
   * @param message the message of each finding, with {@code {value}} as the name
   */
  public NodeUseBeforeDefineCheck(final Options options, final String message) {
    this.options = Objects.requireNonNull(options, "options");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the use-before-define check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final NameReference reference : scopes.references()) {
      if (reference.resolved().isPresent()
          && checked(reference.resolved().get())
          && precedes(reference, reference.resolved().get())) {
        matches.add(new RuleMatch(reference.span(), message.replace("{value}", reference.name())));
      }
    }
    return List.copyOf(matches);
  }

  /** A use textually before the declaration; hoisted functions are checked this way too. */
  private static boolean precedes(final NameReference reference, final Binding binding) {
    return reference.span().startOffset() < binding.declaration().startOffset();
  }

  private boolean checked(final Binding binding) {
    return switch (binding.kind()) {
      case FUNCTION -> options.functions();
      case CLASS -> options.classes();
      case VARIABLE, CONSTANT -> options.variables();
      default -> false;
    };
  }
}
