package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves the names of one Python file: what a name denotes in an expression, and which type a
 * name stands for in an annotation.
 */
final class PythonNameTypes {

  private static final String TYPING = "typing";
  private static final Map<String, String> TYPING_ALIASES =
      Map.of(
          "List", "list", "Dict", "dict", "Set", "set", "FrozenSet", "frozenset", "Tuple", "tuple");

  private final PythonTypeIndex index;
  private final PythonStubs stubs;
  private final PythonMembers members;
  private final PythonAnnotationTypes annotations = new PythonAnnotationTypes(this::annotationName);

  /** Creates the resolver over the indexed file, its stubs and the members it can reach. */
  PythonNameTypes(
      final PythonTypeIndex index, final PythonStubs stubs, final PythonMembers members) {
    this.index = index;
    this.stubs = stubs;
    this.members = members;
  }

  /** The type that an annotation written in this file declares, when it is known. */
  Optional<TypeRef> annotationType(final SyntaxNode annotation) {
    return annotations.of(annotation);
  }

  /**
   * What the name {@code node} denotes. {@code initializerType} gives the type of a variable's
   * initializer, for a variable without an annotation.
   */
  PythonSubject subjectOf(
      final SyntaxNode node, final Function<SyntaxNode, Optional<TypeRef>> initializerType) {
    final Optional<Binding> binding = index.bindingOf(node);
    return binding.isPresent()
        ? bound(binding.get(), initializerType)
        : builtinName(PythonNames.identifier(node));
  }

  /** The built-in class {@code name} as a value, unless this file shadows it. */
  PythonSubject builtinValue(final String name) {
    return index.declaresClass(name)
        ? PythonSubject.UNKNOWN
        : PythonSubject.value(TypeRef.of(name));
  }

  private Optional<TypeRef> annotationName(final SyntaxNode name) {
    final String text = PythonNames.identifier(name);
    final Optional<Binding> binding = index.bindingOf(name);
    if (binding.isEmpty()) {
      return stubs.isClass(text) ? Optional.of(TypeRef.of(text)) : Optional.empty();
    }
    return switch (binding.get().kind()) {
      case IMPORT -> importedType(binding.get().origin());
      case CLASS ->
          index.declaresClass(text) && stubs.isClass(text)
              ? Optional.empty()
              : Optional.of(TypeRef.of(text));
      default -> Optional.empty();
    };
  }

  private Optional<TypeRef> importedType(final Optional<ImportOrigin> origin) {
    if (origin.isEmpty() || origin.get().member().isEmpty()) {
      return Optional.empty();
    }
    final ImportOrigin found = origin.get();
    if (TYPING.equals(found.specifier()) && TYPING_ALIASES.containsKey(found.member())) {
      return Optional.of(TypeRef.of(TYPING_ALIASES.get(found.member())));
    }
    final String qualified = found.specifier() + "." + found.member();
    return stubs.isClass(qualified) ? Optional.of(TypeRef.of(qualified)) : Optional.empty();
  }

  private PythonSubject bound(
      final Binding binding, final Function<SyntaxNode, Optional<TypeRef>> initializerType) {
    return switch (binding.kind()) {
      case PARAMETER, VARIABLE ->
          PythonSubject.value(declaredType(binding, initializerType).orElse(null));
      case FUNCTION ->
          index.function(binding).map(PythonSubject::sourceFunction).orElse(PythonSubject.UNKNOWN);
      case CLASS -> PythonSubject.klass(binding.name());
      case IMPORT -> imported(binding.origin());
      default -> PythonSubject.UNKNOWN;
    };
  }

  private Optional<TypeRef> declaredType(
      final Binding binding, final Function<SyntaxNode, Optional<TypeRef>> initializerType) {
    final Optional<TypeRef> annotated = index.annotation(binding).flatMap(annotations::of);
    return annotated.isPresent() ? annotated : index.initializer(binding).flatMap(initializerType);
  }

  private PythonSubject imported(final Optional<ImportOrigin> origin) {
    if (origin.isEmpty()) {
      return PythonSubject.UNKNOWN;
    }
    final ImportOrigin found = origin.get();
    return found.member().isEmpty()
        ? PythonSubject.module(found.specifier())
        : members.moduleMember(found.specifier(), found.member());
  }

  private PythonSubject builtinName(final String name) {
    if (index.declaresClass(name)) {
      return PythonSubject.UNKNOWN;
    }
    if (stubs.isClass(name)) {
      return PythonSubject.klass(name);
    }
    return stubs
        .function(PythonQualifiedNames.BUILTINS, name)
        .map(function -> PythonSubject.stubFunction(function, null))
        .orElse(PythonSubject.UNKNOWN);
  }
}
