package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Indexes one Python tree by span: its nodes, the annotations and initializers of its declarations,
 * its function and class definitions, and the resolved names of its scopes.
 */
final class PythonTypeIndex {

  private final Map<String, SyntaxNode> nodes = new HashMap<>();
  private final Map<String, SyntaxNode> annotations = new HashMap<>();
  private final Map<String, SyntaxNode> initializers = new HashMap<>();
  private final Map<String, SyntaxNode> functions = new HashMap<>();
  private final Set<String> classNames = new HashSet<>();
  private final Map<String, NameReference> references = new HashMap<>();
  private final Map<String, Binding> bindings = new HashMap<>();
  private final UnitScopes scopes;

  /** Indexes {@code tree}, whose names {@code scopes} resolved. */
  PythonTypeIndex(final SyntaxNode tree, final UnitScopes scopes) {
    this.scopes = scopes;
    collect(tree);
    for (final NameReference reference : scopes.references()) {
      references.putIfAbsent(UnitTypeFacts.startKey(reference.span()), reference);
    }
    for (final Binding binding : scopes.bindings()) {
      bindings.putIfAbsent(UnitTypeFacts.startKey(binding.declaration()), binding);
    }
  }

  /** Returns the node whose span is exactly {@code span}. */
  Optional<SyntaxNode> node(final Span span) {
    return Optional.ofNullable(nodes.get(UnitTypeFacts.key(span)));
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
    return Optional.ofNullable(annotations.get(declarationKey(binding)));
  }

  /** Returns the initializer of {@code binding} when it is assigned exactly once. */
  Optional<SyntaxNode> initializer(final Binding binding) {
    return reassigned(binding)
        ? Optional.empty()
        : Optional.ofNullable(initializers.get(declarationKey(binding)));
  }

  /** Returns the {@code funcdef} that declares the function {@code binding}. */
  Optional<SyntaxNode> function(final Binding binding) {
    return Optional.ofNullable(functions.get(declarationKey(binding)));
  }

  /** Whether the file itself defines a class called {@code name}. */
  boolean declaresClass(final String name) {
    return classNames.contains(name);
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

  private void collect(final SyntaxNode node) {
    if (PythonTypeKinds.COMMENT.equals(node.kind())) {
      return;
    }
    nodes.putIfAbsent(UnitTypeFacts.key(node.span()), node);
    index(node);
    node.children().forEach(this::collect);
  }

  private void index(final SyntaxNode node) {
    switch (node.kind()) {
      case PythonTypeKinds.TFPDEF -> indexParameter(node);
      case PythonTypeKinds.EXPR_STMT -> indexAssignment(node);
      case PythonTypeKinds.FUNCDEF -> indexFunction(node);
      case PythonTypeKinds.CLASSDEF ->
          classNames.add(PythonNames.identifier(node.children().get(1)));
      default -> {
        // Only the declaring nodes above carry facts for the index.
      }
    }
  }

  private void indexParameter(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 3) {
      annotations.put(UnitTypeFacts.startKey(parts.get(0).span()), parts.get(2));
    }
  }

  private void indexAssignment(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 3 && PythonTypeKinds.isToken(parts.get(1), "=")) {
      plainName(parts.get(0))
          .ifPresent(target -> initializers.put(declarationKey(target), parts.get(2)));
    } else if (parts.size() == 2 && PythonTypeKinds.ANNASSIGN.equals(parts.get(1).kind())) {
      indexAnnotatedAssignment(parts.get(0), parts.get(1).children());
    }
  }

  private void indexAnnotatedAssignment(final SyntaxNode target, final List<SyntaxNode> annassign) {
    plainName(target)
        .ifPresent(
            name -> {
              annotations.put(declarationKey(name), annassign.get(1));
              if (annassign.size() == 4) {
                initializers.put(declarationKey(name), annassign.get(3));
              }
            });
  }

  private void indexFunction(final SyntaxNode node) {
    functions.put(UnitTypeFacts.startKey(node.children().get(1).span()), node);
  }

  private static Optional<SyntaxNode> plainName(final SyntaxNode node) {
    final SyntaxNode inner = PythonTypeKinds.descend(node);
    return PythonTypeKinds.isName(inner) ? Optional.of(inner) : Optional.empty();
  }

  private static String declarationKey(final SyntaxNode name) {
    return UnitTypeFacts.startKey(name.span());
  }
}
