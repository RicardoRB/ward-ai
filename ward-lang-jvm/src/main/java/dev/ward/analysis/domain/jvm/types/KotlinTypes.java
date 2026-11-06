package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads Kotlin types and maps the Kotlin built-ins to the JVM types they compile to. */
final class KotlinTypes {
  private static final Map<String, String> PRIMITIVES =
      Map.of(
          "Int", "int", "Long", "long", "Short", "short", "Byte", "byte",
          "Float", "float", "Double", "double", "Boolean", "boolean", "Char", "char", "Unit", "void");
  private static final Map<String, String> CLASSES =
      Map.ofEntries(
          Map.entry("String", Types.STRING),
          Map.entry("Any", Types.OBJECT),
          Map.entry("Number", "java.lang.Number"),
          Map.entry("CharSequence", "java.lang.CharSequence"),
          Map.entry("Comparable", "java.lang.Comparable"),
          Map.entry("Throwable", "java.lang.Throwable"),
          Map.entry("Iterable", "java.lang.Iterable"),
          Map.entry("MutableIterable", "java.lang.Iterable"),
          Map.entry("Iterator", "java.util.Iterator"),
          Map.entry("Collection", "java.util.Collection"),
          Map.entry("MutableCollection", "java.util.Collection"),
          Map.entry("List", "java.util.List"),
          Map.entry("MutableList", "java.util.List"),
          Map.entry("Set", "java.util.Set"),
          Map.entry("MutableSet", "java.util.Set"),
          Map.entry("Map", "java.util.Map"),
          Map.entry("MutableMap", "java.util.Map"),
          Map.entry("Exception", "java.lang.Exception"),
          Map.entry("RuntimeException", "java.lang.RuntimeException"));

  private KotlinTypes() {}

  /** Reads the dotted names of an {@code identifier} node. */
  static List<String> segments(final SyntaxNode identifier) {
    return Syntax.tokens(identifier).stream().filter(token -> !".".equals(token)).toList();
  }

  /** Reads the imports and package of a Kotlin file. */
  static Imports imports(final SyntaxNode root) {
    final java.util.Map<String, String> single = new java.util.HashMap<>();
    final List<String> onDemand = new ArrayList<>();
    for (final SyntaxNode child : root.children()) {
      Syntax.find(child, "importList")
          .ifPresent(
              list -> {
                for (final SyntaxNode header : Syntax.children(list, "importHeader")) {
                  Syntax.child(header, "identifier")
                      .ifPresent(
                          name -> {
                            final String dotted = String.join(".", segments(name));
                            if (Syntax.text(header).endsWith("*")) {
                              onDemand.add(dotted);
                            } else {
                              single.put(dotted.substring(dotted.lastIndexOf('.') + 1), dotted);
                            }
                          });
                }
              });
    }
    return new Imports(packageOf(root), single, onDemand, Map.of(), List.of());
  }

  private static String packageOf(final SyntaxNode root) {
    return Syntax.find(root, "packageHeader")
        .flatMap(header -> Syntax.child(header, "identifier"))
        .map(name -> String.join(".", segments(name)))
        .orElse("");
  }

  /** Returns the type that the {@code type} node writes. */
  static Optional<JType> type(final SyntaxNode node, final TypeScope scope) {
    final Optional<SyntaxNode> user = Syntax.find(node, "userType");
    if (user.isEmpty()) {
      return Optional.empty();
    }
    final boolean nullable = Syntax.text(node).endsWith("?");
    final List<String> names = new ArrayList<>();
    List<JType> arguments = List.of();
    for (final SyntaxNode simple : Syntax.children(user.get(), "simpleUserType")) {
      names.add(Syntax.text(Syntax.child(simple, "simpleIdentifier").orElse(simple)));
      arguments = Syntax.child(simple, "typeArguments").map(found -> arguments(found, scope)).orElse(List.of());
    }
    return named(names, arguments, nullable, scope);
  }

  private static List<JType> arguments(final SyntaxNode node, final TypeScope scope) {
    final List<JType> found = new ArrayList<>();
    for (final SyntaxNode projection : Syntax.children(node, "typeProjection")) {
      final Optional<JType> argument =
          Syntax.child(projection, "type").flatMap(written -> type(written, scope)).map(Types::boxed);
      if (argument.isEmpty()) {
        return List.of();
      }
      found.add(argument.get());
    }
    return found;
  }

  private static Optional<JType> named(
      final List<String> names, final List<JType> arguments, final boolean nullable, final TypeScope scope) {
    final String first = names.get(0);
    if (names.size() == 1 && scope.isTypeVariable(first)) {
      return Optional.of(new JType.TypeVariable(first));
    }
    if (names.size() == 1 && PRIMITIVES.containsKey(first) && scope.imports().single().get(first) == null) {
      final JType primitive = new JType.Primitive(PRIMITIVES.get(first));
      return Optional.of(nullable ? Types.boxed(primitive) : primitive);
    }
    if (names.size() == 1 && "Array".equals(first) && arguments.size() == 1) {
      return Optional.of(new JType.ArrayType(arguments.get(0)));
    }
    if (names.size() == 1 && CLASSES.containsKey(first) && scope.imports().single().get(first) == null) {
      return Optional.of(new JType.ClassType(CLASSES.get(first), arguments));
    }
    return scope.resolve(names).map(binary -> new JType.ClassType(binary, arguments));
  }
}
