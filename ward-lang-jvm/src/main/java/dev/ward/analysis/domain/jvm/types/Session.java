package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The state of typing one unit: what the scopes resolve, and what typing has found so far. */
final class Session {
  final JvmClasses classes;
  final Hierarchy hierarchy;
  final Overloads overloads;
  final Inference inference;
  final Assignability assignability;
  final Map<Long, Typed> expressions = new HashMap<>();
  final Map<Long, DeclInfo> declarations = new HashMap<>();
  private final ScopeIndex scopes;
  private final Map<Integer, Optional<JType>> variables = new HashMap<>();

  Session(final JvmClasses classes, final UnitScopes unitScopes) {
    this.classes = classes;
    this.hierarchy = new Hierarchy(classes);
    this.assignability = new Assignability(hierarchy);
    this.inference = new Inference(hierarchy, assignability);
    this.overloads = new Overloads(assignability, inference);
    this.scopes = new ScopeIndex(unitScopes);
  }

  static long key(final Span span) {
    return (long) span.startOffset() << Integer.SIZE | span.endOffset();
  }

  void record(final SyntaxNode node, final Typed typed) {
    expressions.putIfAbsent(key(node.span()), typed);
  }

  void declare(final SyntaxNode node, final DeclInfo info) {
    declarations.putIfAbsent(key(node.span()), info);
  }

  /** Remembers the type of the variable whose name starts at {@code identifier}. */
  void variable(final SyntaxNode identifier, final Optional<JType> type) {
    variables.put(identifier.span().startOffset(), type);
  }

  /** Returns the binding a name resolved to, if any. */
  Optional<Binding> binding(final SyntaxNode identifier) {
    return scopes.binding(identifier);
  }

  /** Returns whether a variable was declared at the binding, and its type. */
  Optional<Optional<JType>> variableAt(final Binding binding) {
    return Optional.ofNullable(variables.get(binding.declaration().startOffset()));
  }

  /** Returns the type of the variable declared at {@code identifier}. */
  Optional<JType> variableType(final SyntaxNode identifier) {
    return variables.getOrDefault(identifier.span().startOffset(), Optional.empty());
  }

  boolean isDeclaration(final SyntaxNode identifier) {
    return scopes.isDeclaration(identifier);
  }

  /** Returns the type of {@code binary} with its own type parameters as arguments. */
  JType.ClassType self(final String binary) {
    final List<JType> arguments =
        hierarchy.find(binary).stream()
            .flatMap(owner -> owner.typeParameters().stream())
            .map(parameter -> (JType) new JType.TypeVariable(parameter.name()))
            .toList();
    return new JType.ClassType(binary, arguments);
  }
}
