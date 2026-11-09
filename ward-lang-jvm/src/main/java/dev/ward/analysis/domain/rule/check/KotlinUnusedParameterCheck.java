package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each parameter of a Kotlin function that the body never reads, as detekt's {@code
 * UnusedParameter} does. Names matching {@code ignored|expected} are allowed. A name the file
 * mentions anywhere else, such as in a string template the scopes do not read, counts as used. A
 * function that is an override, open, abstract, operator, actual or expect declaration is left
 * alone, since its parameters are fixed by a signature.
 */
public final class KotlinUnusedParameterCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final Pattern ALLOWED_NAMES = Pattern.compile("ignored|expected");
  private static final Set<String> SIGNATURE_MODIFIERS =
      Set.of("override", "open", "abstract", "operator", "actual", "expect");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public KotlinUnusedParameterCheck(final String message) {
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
      if (scope.kind() == ScopeKind.FUNCTION && !scope.open() && !signature(scope, unit.text())) {
        scope.bindings().stream()
            .filter(binding -> unused(binding, scopes, unit.text()))
            .forEach(binding -> found.add(match(binding)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return List.copyOf(found);
  }

  private RuleMatch match(final Binding binding) {
    return new RuleMatch(binding.declaration(), message.replace(NAME, binding.name()));
  }

  private static boolean unused(final Binding binding, final UnitScopes scopes, final String text) {
    return binding.kind() == BindingKind.PARAMETER
        && !ALLOWED_NAMES.matcher(binding.name()).matches()
        && scopes.usesOf(binding).stream().noneMatch(use -> use.access() != ReferenceAccess.WRITE)
        && !KotlinDeclarations.usedElsewhere(text, binding.name(), binding.declaration());
  }

  private static boolean signature(final Scope scope, final String text) {
    final String modifiers =
        KotlinDeclarations.modifiersBeforeFun(text, scope.span().startOffset());
    return SIGNATURE_MODIFIERS.stream()
        .anyMatch(word -> KotlinDeclarations.hasWord(modifiers, word));
  }
}
