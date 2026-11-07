package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads the methods a class, enum or record declares directly in its body. */
final class TypeMethods {

  /** The kinds of the declarations that have a body of members. */
  static final Set<String> TYPES =
      Set.of("classDeclaration", "enumDeclaration", "recordDeclaration");

  private static final String METHOD = "methodDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String PARAMETERS = "formalParameters";
  private static final String PARAMETER = "formalParameter";
  private static final String TYPE = "typeType";
  private static final Set<String> OBJECT = Set.of("Object", "java.lang.Object");
  private static final Set<String> MEMBER_CONTAINERS =
      Set.of("classBody", "classBodyDeclaration", "memberDeclaration", "enumBodyDeclarations");

  private TypeMethods() {}

  /** Returns the methods declared directly in the body of {@code type}, in source order. */
  static List<SyntaxNode> of(final SyntaxNode type) {
    final List<SyntaxNode> methods = new ArrayList<>();
    collect(type, methods);
    return methods;
  }

  /** Returns the name of {@code method}, or an empty string if it has none. */
  static String name(final SyntaxNode method) {
    return child(method, IDENTIFIER).map(SyntaxWalk::text).orElse("");
  }

  /** Returns the declared type of each parameter of {@code method}, as written. */
  static List<String> parameterTypes(final SyntaxNode method) {
    final List<String> types = new ArrayList<>();
    child(method, PARAMETERS)
        .ifPresent(
            list -> {
              for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(list, Set.of(PARAMETER))) {
                child(visit.node(), TYPE).map(SyntaxWalk::text).ifPresent(types::add);
              }
            });
    return types;
  }

  /** Tells whether a parameter type is {@code Object}, written with or without its package. */
  static boolean isObject(final String type) {
    return OBJECT.contains(type);
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> methods) {
    for (final SyntaxNode child : node.children()) {
      if (METHOD.equals(child.kind())) {
        methods.add(child);
      } else if (MEMBER_CONTAINERS.contains(child.kind())) {
        collect(child, methods);
      }
    }
  }

  private static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst();
  }
}
