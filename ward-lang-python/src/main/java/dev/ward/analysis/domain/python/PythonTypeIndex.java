package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Indexes one Python tree by span: its nodes and declarations, and the resolved names of its
 * scopes.
 */
final class PythonTypeIndex {

  private final PythonDeclarations declarations;
  private final Map<String, NameReference> references = new HashMap<>();
  private final Map<String, Binding> bindings = new HashMap<>();
  private final UnitScopes scopes;

  /** Indexes {@code tree}, whose names {@code scopes} resolved. */
  PythonTypeIndex(final SyntaxNode tree, final UnitScopes scopes) {
    this.scopes = scopes;
    this.declarations = PythonDeclarations.of(tree);
    for (final NameReference reference : scopes.references()) {
      references.putIfAbsent(UnitTypeFacts.startKey(reference.span()), reference);
    }
    for (final Binding binding : scopes.bindings()) {
      bindings.putIfAbsent(UnitTypeFacts.startKey(binding.declaration()), binding);
    }
  }

  /** Returns the node whose span is exactly {@code span}. */
  Optional<SyntaxNode> node(final Span span) {
    return Optional.ofNullable(declarations.nodes().get(UnitTypeFacts.key(span)));
  }

  /** Returns the binding that the name at {@code name} refers to or declares. */
  Optional<Binding> bindingOf(final SyntaxNode name) {
    final String start = UnitTypeFacts.startKey(name.span());
    final Optional<Binding> resolved =
        Optional.ofNullable(references.get(start)).flatMap(NameReference::resolved);
    return resolved.isPresent() ? resolved : Optional.ofNullable(bindings.get(start));
  }

  /** Returns the annotation written for the declaration {@code binding}, if any. */
  Optional<SyntaxNode> annotation(final Binding binding) {
    return Optional.ofNullable(declarations.annotations().get(declarationKey(binding)));
  }

  /** Returns the initializer of {@code binding} when it is assigned exactly once. */
  Optional<SyntaxNode> initializer(final Binding binding) {
    return reassigned(binding)
        ? Optional.empty()
        : Optional.ofNullable(declarations.initializers().get(declarationKey(binding)));
  }

  /** Returns the {@code funcdef} that declares the function {@code binding}. */
  Optional<SyntaxNode> function(final Binding binding) {
    return Optional.ofNullable(declarations.functions().get(declarationKey(binding)));
  }

  /** Whether the file itself defines a class called {@code name}. */
  boolean declaresClass(final String name) {
    return declarations.classNames().contains(name);
  }

  private boolean reassigned(final Binding binding) {
    final String declaration = declarationKey(binding);
    final List<NameReference> uses = scopes.usesOf(binding);
    return uses.stream()
        .anyMatch(
            use ->
                use.access() != ReferenceAccess.READ
                    && !declaration.equals(UnitTypeFacts.startKey(use.span())));
  }

  private static String declarationKey(final Binding binding) {
    return UnitTypeFacts.startKey(binding.declaration());
  }
}
