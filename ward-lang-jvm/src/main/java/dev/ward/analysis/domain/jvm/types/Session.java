package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The state of typing one unit: what the scopes resolve, and what typing has found so far. */
final class Session {
  final JvmClasses classes;
  final Hierarchy hierarchy;
  final Overloads overloads;
  final Inference inference;
  final Assignability assignability;
  final Map<Long, Typed> expressions = new HashMap<>();
  final Map<Long, DeclInfo> declarations = new HashMap<>();
  private final Map<Integer, NameReference> references = new HashMap<>();
  private final Set<Integer> declared = new HashSet<>();
  private final Map<Integer, Optional<JType>> variables = new HashMap<>();

  Session(final JvmClasses classes, final UnitScopes scopes) {
    this.classes = classes;
    this.hierarchy = new Hierarchy(classes);
    this.assignability = new Assignability(hierarchy);
    this.inference = new Inference(hierarchy, assignability);
    this.overloads = new Overloads(assignability, inference);
    for (final NameReference reference : scopes.references()) {
      references.putIfAbsent(reference.span().startOffset(), reference);
    }
    for (final Binding binding : scopes.bindings()) {
      declared.add(binding.declaration().startOffset());
    }
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
    final NameReference reference = references.get(identifier.span().startOffset());
    return reference == null ? Optional.empty() : reference.resolved();
  }

  /** Returns whether a variable was declared at {@code identifier}, and its type. */
  Optional<Optional<JType>> variableAt(final Binding binding) {
    return Optional.ofNullable(variables.get(binding.declaration().startOffset()));
  }

  /** Returns the type of the variable declared at {@code identifier}. */
  Optional<JType> variableType(final SyntaxNode identifier) {
    return variables.getOrDefault(identifier.span().startOffset(), Optional.empty());
  }

  /** Returns whether the binding names a value, not a type, an import or a function. */
  static boolean isValue(final Binding binding) {
    return switch (binding.kind()) {
      case VARIABLE, PARAMETER, CONSTANT -> true;
      default -> false;
    };
  }

  boolean isDeclaration(final SyntaxNode identifier) {
    return declared.contains(identifier.span().startOffset());
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
