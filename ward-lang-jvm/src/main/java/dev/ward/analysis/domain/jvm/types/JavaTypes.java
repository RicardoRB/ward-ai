package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads Java types from the syntax tree. */
final class JavaTypes {
  private static final String TYPE_TYPE = "typeType";
  private static final String CLASS_TYPE = "classType";
  private static final String IDENTIFIER = "identifier";
  private static final String BRACKET = "[";
  private static final String WILDCARD = "?";
  private static final String EXTENDS = "extends";

  private JavaTypes() {}

  /** Returns the type that the {@code typeType} node writes, empty when a name does not resolve. */
  static Optional<JType> type(final SyntaxNode node, final TypeScope scope) {
    final Optional<JType> base =
        node.children().stream()
            .map(child -> base(child, scope))
            .flatMap(Optional::stream)
            .findFirst();
    final int dimensions =
        (int) node.children().stream().filter(child -> BRACKET.equals(Syntax.token(child))).count();
    return base.map(found -> arrayOf(found, dimensions));
  }

  private static Optional<JType> base(final SyntaxNode child, final TypeScope scope) {
    return switch (child.kind()) {
      case "primitiveType" -> Optional.of(new JType.Primitive(Syntax.text(child)));
      case "classOrInterfaceType" ->
          Syntax.child(child, CLASS_TYPE).flatMap(found -> classType(found, scope));
      default -> Optional.empty();
    };
  }

  /** Returns the type of a {@code typeTypeOrVoid} node. */
  static Optional<JType> typeOrVoid(final SyntaxNode node, final TypeScope scope) {
    return Syntax.child(node, TYPE_TYPE)
        .flatMap(type -> type(type, scope))
        .or(() -> Optional.of(new JType.Primitive("void")));
  }

  static JType arrayOf(final JType component, final int dimensions) {
    JType type = component;
    for (int count = 0; count < dimensions; count++) {
      type = wrap(type);
    }
    return type;
  }

  private static JType wrap(final JType component) {
    return new JType.ArrayType(component);
  }

  /** Returns the class or type variable that a {@code classType} node writes. */
  static Optional<JType> classType(final SyntaxNode node, final TypeScope scope) {
    final List<String> segments = new ArrayList<>();
    List<JType> arguments = List.of();
    for (final SyntaxNode child : node.children()) {
      switch (child.kind()) {
        case "packageName" ->
            segments.addAll(
                Syntax.children(child, IDENTIFIER).stream().map(Syntax::text).toList());
        case "typeIdentifier" -> {
          segments.add(Syntax.text(child));
          arguments = List.of();
        }
        case "typeArguments" -> arguments = typeArguments(child, scope);
        default -> {
          // Dots and annotations carry no name.
        }
      }
    }
    if (segments.size() == 1 && scope.isTypeVariable(segments.get(0))) {
      return Optional.of(new JType.TypeVariable(segments.get(0)));
    }
    final List<JType> written = arguments;
    return scope.resolve(segments).map(name -> new JType.ClassType(name, written));
  }

  /** Returns the arguments; empty when any one does not resolve, so the type is left raw. */
  static List<JType> typeArguments(final SyntaxNode node, final TypeScope scope) {
    final List<Optional<JType>> resolved =
        Syntax.children(node, "typeArgument").stream().map(arg -> argument(arg, scope)).toList();
    return resolved.stream().allMatch(Optional::isPresent)
        ? resolved.stream().map(Optional::get).toList()
        : List.of();
  }

  private static Optional<JType> argument(final SyntaxNode node, final TypeScope scope) {
    final Optional<SyntaxNode> written = Syntax.child(node, TYPE_TYPE);
    if (!Syntax.hasToken(node, WILDCARD)) {
      return written.flatMap(type -> type(type, scope));
    }
    if (written.isEmpty()) {
      return Optional.of(new JType.Wildcard(null, true));
    }
    return type(written.get(), scope)
        .map(bound -> new JType.Wildcard(bound, Syntax.hasToken(node, EXTENDS)));
  }

  /** Returns the types of a {@code typeList}; a type that does not resolve is left out. */
  static List<JType> typeList(final SyntaxNode node, final TypeScope scope) {
    return Syntax.children(node, TYPE_TYPE).stream()
        .map(type -> type(type, scope))
        .flatMap(Optional::stream)
        .toList();
  }

  /** Returns the type parameters declared by the {@code typeParameters} child of {@code node}. */
  static List<TypeParameter> typeParameters(final SyntaxNode node, final TypeScope scope) {
    final List<SyntaxNode> declared =
        Syntax.child(node, "typeParameters")
            .map(found -> Syntax.children(found, "typeParameter"))
            .orElse(List.of());
    final TypeScope inner =
        scope.withTypeVariables(declared.stream().map(JavaTypes::nameOf).toList());
    return declared.stream().map(parameter -> typeParameter(parameter, inner)).toList();
  }

  private static String nameOf(final SyntaxNode parameter) {
    return Syntax.text(Syntax.child(parameter, IDENTIFIER).orElse(parameter));
  }

  private static TypeParameter typeParameter(final SyntaxNode node, final TypeScope scope) {
    final List<JType> bounds =
        Syntax.child(node, "typeBound").map(bound -> typeList(bound, scope)).orElse(List.of());
    return new TypeParameter(nameOf(node), bounds);
  }
}
