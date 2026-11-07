package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The names of a unit by position: what each use resolves to, and where each name is declared. */
final class ScopeIndex {
  private final Map<Integer, NameReference> references;
  private final Set<Integer> declared;

  ScopeIndex(final UnitScopes scopes) {
    this.references =
        scopes.references().stream()
            .collect(
                Collectors.toMap(
                    reference -> reference.span().startOffset(),
                    Function.identity(),
                    (first, second) -> first));
    this.declared =
        scopes.bindings().stream()
            .map(binding -> binding.declaration().startOffset())
            .collect(Collectors.toSet());
  }

  /** Returns the binding a name resolved to, if any. */
  Optional<Binding> binding(final SyntaxNode identifier) {
    return Optional.ofNullable(references.get(identifier.span().startOffset()))
        .flatMap(NameReference::resolved);
  }

  boolean isDeclaration(final SyntaxNode identifier) {
    return declared.contains(identifier.span().startOffset());
  }

  /** Returns whether the binding names a value, not a type, an import or a function. */
  static boolean isValue(final Binding binding) {
    return switch (binding.kind()) {
      case VARIABLE, PARAMETER, CONSTANT -> true;
      default -> false;
    };
  }
}
