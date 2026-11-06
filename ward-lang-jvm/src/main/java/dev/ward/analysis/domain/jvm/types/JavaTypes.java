package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads Java types, modifiers and annotations from the syntax tree. */
final class JavaTypes {
  private static final Set<String> PRIMITIVES =
      Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double");
  private static final String TYPE_TYPE = "typeType";
  private static final String CLASS_TYPE = "classType";
  private static final String ANNOTATION = "annotation";

  private JavaTypes() {}

  /** Returns the type that the {@code typeType} node writes, empty when a name does not resolve. */
  static Optional<JType> type(final SyntaxNode node, final TypeScope scope) {
    Optional<JType> base = Optional.empty();
    int dimensions = 0;
    for (final SyntaxNode child : node.children()) {
      if ("primitiveType".equals(child.kind())) {
        base = Optional.of(new JType.Primitive(Syntax.text(child)));
      } else if ("classOrInterfaceType".equals(child.kind())) {
        base = Syntax.child(child, CLASS_TYPE).flatMap(found -> classType(found, scope));
      } else if ("[".equals(Syntax.token(child))) {
        dimensions++;
      }
    }
    final int depth = dimensions;
    return base.map(found -> arrayOf(found, depth));
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
      type = new JType.ArrayType(type);
    }
    return type;
  }

  /** Returns the class or type variable that a {@code classType} node writes. */
  static Optional<JType> classType(final SyntaxNode node, final TypeScope scope) {
    final List<String> segments = new ArrayList<>();
    List<JType> arguments = List.of();
    for (final SyntaxNode child : node.children()) {
      if ("packageName".equals(child.kind())) {
        segments.addAll(identifiers(child));
      } else if ("typeIdentifier".equals(child.kind())) {
        segments.add(Syntax.text(child));
        arguments = List.of();
      } else if ("typeArguments".equals(child.kind())) {
        arguments = typeArguments(child, scope);
      }
    }
    if (segments.size() == 1 && scope.isTypeVariable(segments.get(0))) {
      return Optional.of(new JType.TypeVariable(segments.get(0)));
    }
    final List<JType> written = arguments;
    return scope.resolve(segments).map(name -> new JType.ClassType(name, written));
  }

  private static List<String> identifiers(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> "identifier".equals(child.kind()))
        .map(Syntax::text)
        .toList();
  }

  /** Returns the arguments; empty when any one does not resolve, so the type is left raw. */
  static List<JType> typeArguments(final SyntaxNode node, final TypeScope scope) {
    final List<JType> arguments = new ArrayList<>();
    for (final SyntaxNode argument : Syntax.children(node, "typeArgument")) {
      final Optional<JType> resolved = argument(argument, scope);
      if (resolved.isEmpty()) {
        return List.of();
      }
      arguments.add(resolved.get());
    }
    return arguments;
  }

  private static Optional<JType> argument(final SyntaxNode node, final TypeScope scope) {
    final Optional<SyntaxNode> written = Syntax.child(node, TYPE_TYPE);
    if (!Syntax.hasToken(node, "?")) {
      return written.flatMap(type -> type(type, scope));
    }
    if (written.isEmpty()) {
      return Optional.of(new JType.Wildcard(null, true));
    }
    return type(written.get(), scope)
        .map(bound -> new JType.Wildcard(bound, Syntax.hasToken(node, "extends")));
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
    final Optional<SyntaxNode> declared = Syntax.child(node, "typeParameters");
    if (declared.isEmpty()) {
      return List.of();
    }
    final TypeScope inner =
        scope.withTypeVariables(
            Syntax.children(declared.get(), "typeParameter").stream()
                .map(parameter -> Syntax.text(Syntax.child(parameter, "identifier").orElse(parameter)))
                .toList());
    return Syntax.children(declared.get(), "typeParameter").stream()
        .map(parameter -> typeParameter(parameter, inner))
        .toList();
  }

  private static TypeParameter typeParameter(final SyntaxNode node, final TypeScope scope) {
    final String name = Syntax.text(Syntax.child(node, "identifier").orElse(node));
    final List<JType> bounds =
        Syntax.child(node, "typeBound").map(bound -> typeList(bound, scope)).orElse(List.of());
    return new TypeParameter(name, bounds);
  }

  /** Returns the access flags that the modifier nodes write. */
  static int flags(final List<SyntaxNode> modifiers) {
    int flags = 0;
    for (final SyntaxNode modifier : modifiers) {
      if (Syntax.find(modifier, ANNOTATION).isPresent()) {
        continue;
      }
      flags |= switch (Syntax.text(modifier)) {
        case "public" -> Flags.PUBLIC;
        case "private" -> Flags.PRIVATE;
        case "protected" -> Flags.PROTECTED;
        case "static" -> Flags.STATIC;
        case "final" -> Flags.FINAL;
        case "abstract" -> Flags.ABSTRACT;
        default -> 0;
      };
    }
    return flags;
  }

  /** Returns whether a modifier node writes {@code word}. */
  static boolean has(final List<SyntaxNode> modifiers, final String word) {
    return modifiers.stream()
        .filter(modifier -> Syntax.find(modifier, ANNOTATION).isEmpty())
        .anyMatch(modifier -> word.equals(Syntax.text(modifier)));
  }

  /** Returns the qualified names of the annotations among the modifier nodes. */
  static List<String> annotations(final List<SyntaxNode> modifiers, final TypeScope scope) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode modifier : modifiers) {
      Syntax.find(modifier, ANNOTATION)
          .flatMap(annotation -> Syntax.child(annotation, "qualifiedName"))
          .map(name -> annotationName(Syntax.text(name), scope))
          .ifPresent(names::add);
    }
    return names;
  }

  private static String annotationName(final String written, final TypeScope scope) {
    return scope.resolve(List.of(written.split("\\."))).orElse(written);
  }

  /** Returns whether {@code name} is a primitive type name. */
  static boolean isPrimitive(final String name) {
    return PRIMITIVES.contains(name);
  }
}
