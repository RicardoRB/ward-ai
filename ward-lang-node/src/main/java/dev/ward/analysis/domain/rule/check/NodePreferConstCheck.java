package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports each {@code let} variable that is initialized and never assigned again, like ESLint's
 * prefer-const with its default options. Exported bindings are left alone.
 */
public final class NodePreferConstCheck implements RuleCheck {

  private static final String NAME = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public NodePreferConstCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the prefer-const check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      for (final Binding binding : scope.bindings()) {
        if (candidate(binding, unit) && neverAssigned(binding, scopes)) {
          found.add(new RuleMatch(binding.declaration(), message.replace(NAME, binding.name())));
        }
      }
    }
    return List.copyOf(found);
  }

  /**
   * A {@code let} that is initialized where it is declared, which is the only shape that needs
   * const.
   */
  private static boolean candidate(final Binding binding, final SourceUnit unit) {
    return binding.kind() == BindingKind.VARIABLE
        && binding.flags().contains(BindingFlag.LEXICAL)
        && !binding.flags().contains(BindingFlag.EXPORTED)
        && initializedAtDeclaration(binding, unit);
  }

  private static boolean initializedAtDeclaration(final Binding binding, final SourceUnit unit) {
    final String text = unit.text();
    int at = binding.declaration().startOffset() + binding.name().length();
    while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
      at++;
    }
    return at < text.length()
        && text.charAt(at) == '='
        && (at + 1 >= text.length() || text.charAt(at + 1) != '=');
  }

  private static boolean neverAssigned(final Binding binding, final UnitScopes scopes) {
    return scopes.usesOf(binding).stream()
        .noneMatch(
            use ->
                use.access() == ReferenceAccess.WRITE
                    && use.span().startOffset() != binding.declaration().startOffset());
  }
}
