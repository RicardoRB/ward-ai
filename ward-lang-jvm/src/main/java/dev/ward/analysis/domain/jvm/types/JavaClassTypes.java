package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Reads the class types and type variables that Java writes as dotted names. */
final class JavaClassTypes {
  private static final String IDENTIFIER = "identifier";

  private JavaClassTypes() {}

  /** Returns the class or type variable that a {@code classType} node writes. */
  static Optional<JType> of(final SyntaxNode node, final TypeScope scope) {
    final List<String> segments =
        node.children().stream().flatMap(child -> segmentsOf(child).stream()).toList();
    if (segments.size() == 1 && scope.isTypeVariable(segments.get(0))) {
      return Optional.of(new JType.TypeVariable(segments.get(0)));
    }
    final List<JType> arguments = lastArguments(node, scope);
    return scope.resolve(segments).map(name -> new JType.ClassType(name, arguments));
  }

  private static List<String> segmentsOf(final SyntaxNode child) {
    return switch (child.kind()) {
      case "packageName" -> Syntax.children(child, IDENTIFIER).stream().map(Syntax::text).toList();
      case "typeIdentifier" -> List.of(Syntax.text(child));
      default -> List.of();
    };
  }

  /** The arguments written after the last name, or none. */
  private static List<JType> lastArguments(final SyntaxNode node, final TypeScope scope) {
    final List<SyntaxNode> children = node.children();
    final int lastName = lastIndexOf(children, "typeIdentifier");
    return children.stream()
        .skip(lastName + 1L)
        .filter(child -> "typeArguments".equals(child.kind()))
        .findFirst()
        .map(arguments -> JavaTypes.typeArguments(arguments, scope))
        .orElse(List.of());
  }

  private static int lastIndexOf(final List<SyntaxNode> children, final String kind) {
    int last = -1;
    for (int index = 0; index < children.size(); index++) {
      if (kind.equals(children.get(index).kind())) {
        last = index;
      }
    }
    return last;
  }
}
