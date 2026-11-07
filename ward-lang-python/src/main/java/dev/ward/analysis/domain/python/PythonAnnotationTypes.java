package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turns a Python annotation into a {@link TypeRef}. A plain name or a subscript of a plain name is
 * a type; anything else (unions, strings, {@code Optional}, type variables) is unknown.
 */
final class PythonAnnotationTypes {

  private static final String NONE = "None";
  private static final String OPEN_BRACKET = "[";
  private static final int MAX_DEPTH = 6;

  private final Function<SyntaxNode, Optional<TypeRef>> names;

  /** Creates the converter; {@code names} resolves one identifier node to its type. */
  PythonAnnotationTypes(final Function<SyntaxNode, Optional<TypeRef>> names) {
    this.names = names;
  }

  /** Returns the type that {@code annotation} declares, or empty when it is not known. */
  Optional<TypeRef> of(final SyntaxNode annotation) {
    return of(annotation, 0);
  }

  private Optional<TypeRef> of(final SyntaxNode annotation, final int depth) {
    if (depth > MAX_DEPTH) {
      return Optional.empty();
    }
    final SyntaxNode node = PythonTypeKinds.descend(annotation);
    if (PythonTypeKinds.isToken(node, NONE)) {
      return Optional.of(TypeRef.of(NONE));
    }
    if (PythonTypeKinds.isName(node)) {
      return names.apply(node);
    }
    if (PythonTypeKinds.ATOM_EXPR.equals(node.kind())) {
      return subscript(node, depth);
    }
    return Optional.empty();
  }

  private Optional<TypeRef> subscript(final SyntaxNode node, final int depth) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode head = parts.isEmpty() ? node : PythonTypeKinds.descend(parts.get(0));
    if (parts.size() != 2 || !PythonTypeKinds.isName(head)) {
      return Optional.empty();
    }
    final Optional<TypeRef> base = names.apply(head);
    final SyntaxNode trailer = parts.get(1);
    if (base.isEmpty()
        || !PythonTypeKinds.TRAILER.equals(trailer.kind())
        || !PythonTypeKinds.isToken(trailer.children().get(0), OPEN_BRACKET)) {
      return Optional.empty();
    }
    return base.map(type -> new TypeRef(type.name(), arguments(trailer, depth), 0, false));
  }

  /** The converted type arguments, or none when one of them is unknown. */
  private List<TypeRef> arguments(final SyntaxNode trailer, final int depth) {
    final List<TypeRef> arguments = new ArrayList<>();
    for (final SyntaxNode list : trailer.children()) {
      if (PythonTypeKinds.SUBSCRIPTLIST.equals(list.kind())) {
        for (final SyntaxNode argument : PythonNames.elements(list)) {
          final Optional<TypeRef> converted = of(argument, depth + 1);
          if (converted.isEmpty()) {
            return List.of();
          }
          arguments.add(converted.get());
        }
      }
    }
    return arguments;
  }
}
