package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.Scope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Reports each private class that its file never mentions, as detekt's {@code UnusedPrivateClass}
 * does. A mention anywhere in the file, outside the declaration, counts as a use. Annotated classes
 * are left alone.
 */
public final class KotlinUnusedPrivateClassCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final String PRIVATE = "private";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public KotlinUnusedPrivateClassCheck(final String message) {
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
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : view.scopes().scopes()) {
      scope.bindings().stream()
          .filter(binding -> unused(binding, unit.text()))
          .forEach(binding -> found.add(match(binding)));
    }
    found.sort(Comparator.comparingInt(match -> match.span().startOffset()));
    return List.copyOf(found);
  }

  private RuleMatch match(final Binding binding) {
    return new RuleMatch(binding.declaration(), message.replace(NAME, binding.name()));
  }

  private static boolean unused(final Binding binding, final String text) {
    return binding.kind() == BindingKind.CLASS
        && KotlinDeclarations.hasWord(
            KotlinDeclarations.prefixOnLine(text, binding.declaration()), PRIVATE)
        && !KotlinDeclarations.annotated(text, binding.declaration())
        && !KotlinDeclarations.usedElsewhere(text, binding.name(), binding.declaration());
  }
}
