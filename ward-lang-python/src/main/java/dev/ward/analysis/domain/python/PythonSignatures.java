package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parameter and return types of stub functions. A signature with unpacking or positional-only
 * markers, or with a parameter that has no known type, has no known parameter list.
 */
final class PythonSignatures {

  private static final String ANNOTATION_ARROW = "->";
  private static final String UNPACK = "*";
  private static final String KEYWORD_UNPACK = "**";
  private static final String POSITIONAL_ONLY = "/";
  private static final int TYPED_PARAMETER_PARTS = 3;

  private final PythonStubs stubs;

  /** Creates the signatures that resolve their names through {@code stubs}. */
  PythonSignatures(final PythonStubs stubs) {
    this.stubs = stubs;
  }

  /** The parameter types of a stub function; a method skips its receiver. Empty when unknown. */
  List<TypeRef> parameters(final SyntaxNode definition, final String module, final boolean method) {
    final Optional<List<SyntaxNode>> parsed = parameterItems(definition);
    if (parsed.isEmpty()) {
      return List.of();
    }
    final List<SyntaxNode> items = parsed.get();
    final PythonAnnotationTypes annotations =
        new PythonAnnotationTypes(node -> stubName(module, node));
    final List<TypeRef> types = new ArrayList<>();
    for (int position = method ? 1 : 0; position < items.size(); position++) {
      final SyntaxNode item = items.get(position);
      if (item.children().size() != TYPED_PARAMETER_PARTS) {
        return List.of();
      }
      final Optional<TypeRef> type = annotations.of(item.children().get(2));
      if (type.isEmpty()) {
        return List.of();
      }
      types.add(type.get());
    }
    return types;
  }

  /** The return type of a stub function, when its annotation is known. */
  Optional<TypeRef> returns(final SyntaxNode definition, final String module) {
    return returnAnnotation(definition)
        .flatMap(
            annotation -> new PythonAnnotationTypes(node -> stubName(module, node)).of(annotation));
  }

  /** The annotation expression after {@code ->} in a function definition, if written. */
  static Optional<SyntaxNode> returnAnnotation(final SyntaxNode definition) {
    final List<SyntaxNode> parts = definition.children();
    final int arrow = PythonNames.indexOf(parts, ANNOTATION_ARROW);
    return arrow < 0 ? Optional.empty() : Optional.of(parts.get(arrow + 1));
  }

  /**
   * The typed parameters, or empty when the signature has a marker this resolver does not model.
   */
  private static Optional<List<SyntaxNode>> parameterItems(final SyntaxNode definition) {
    final List<SyntaxNode> items = new ArrayList<>();
    final Optional<SyntaxNode> list =
        PythonSyntax.children(definition, PythonTypeKinds.PARAMETERS).stream()
            .flatMap(
                parameters ->
                    PythonSyntax.children(parameters, PythonTypeKinds.TYPEDARGSLIST).stream())
            .findFirst();
    if (list.isEmpty()) {
      return Optional.of(items);
    }
    for (final SyntaxNode item : list.get().children()) {
      final String text = PythonSyntax.text(item);
      if (UNPACK.equals(text) || KEYWORD_UNPACK.equals(text) || POSITIONAL_ONLY.equals(text)) {
        return Optional.empty();
      }
      if (PythonTypeKinds.TFPDEF.equals(item.kind())) {
        items.add(item);
      }
    }
    return Optional.of(items);
  }

  /** Resolves a name in a stub: a class of the same module, or a built-in class. */
  private Optional<TypeRef> stubName(final String module, final SyntaxNode name) {
    final String text = PythonNames.identifier(name);
    final String qualified = PythonQualifiedNames.qualify(module, text);
    if (stubs.isClass(qualified)) {
      return Optional.of(TypeRef.of(qualified));
    }
    return stubs.isClass(text) ? Optional.of(TypeRef.of(text)) : Optional.empty();
  }
}
