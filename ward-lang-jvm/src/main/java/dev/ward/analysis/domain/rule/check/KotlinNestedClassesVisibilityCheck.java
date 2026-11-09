package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each nested class of an {@code internal} top-level class that writes {@code public} or
 * {@code protected} explicitly, as detekt's {@code NestedClassesVisibility} does. Enum classes and
 * companion objects are left alone.
 */
public final class KotlinNestedClassesVisibilityCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final String INTERNAL = "internal";
  private static final Set<String> EXPLICIT = Set.of("public", "protected");
  private static final String ENUM = "enum";
  private static final String COMPANION = "companion";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public KotlinNestedClassesVisibilityCheck(final String message) {
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
        if (binding.kind() == BindingKind.CLASS
            && explicitlyPublic(binding, unit.text())
            && innerOfInternalTopLevel(scope, scopes, unit.text())) {
          found.add(new RuleMatch(binding.declaration(), message.replace(NAME, binding.name())));
        }
      }
    }
    found.sort(Comparator.comparingInt(match -> match.span().startOffset()));
    return List.copyOf(found);
  }

  private static boolean explicitlyPublic(final Binding binding, final String text) {
    final String prefix = KotlinDeclarations.prefixOnLine(text, binding.declaration());
    return EXPLICIT.stream().anyMatch(word -> KotlinDeclarations.hasWord(prefix, word))
        && !KotlinDeclarations.hasWord(prefix, ENUM)
        && !KotlinDeclarations.hasWord(prefix, COMPANION);
  }

  private static boolean innerOfInternalTopLevel(
      final Scope declaring, final UnitScopes scopes, final String text) {
    if (declaring.kind() != ScopeKind.CLASS) {
      return false;
    }
    final Optional<Scope> outer = scopes.parent(declaring);
    return outer.isPresent()
        && outer.get().kind() == ScopeKind.MODULE
        && KotlinDeclarations.hasWord(
            KotlinDeclarations.header(text, declaring.span().startOffset()), INTERNAL);
  }
}
