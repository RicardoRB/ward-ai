package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Reads the type names a class declaration lists after {@code extends} and {@code implements}. */
final class ClassTypeNames {

  private static final Pattern GENERICS = Pattern.compile("<.*>");
  private static final String EXTENDS = "'extends'";
  private static final String IMPLEMENTS = "'implements'";
  private static final String TYPE_TYPE = "typeType";

  private ClassTypeNames() {}

  /** Returns the simple name of a type written in the source, without package or arguments. */
  static String simpleName(final String typeText) {
    final String bare = GENERICS.matcher(typeText).replaceAll("");
    return bare.substring(bare.lastIndexOf('.') + 1);
  }

  /** Returns the simple names of the types a class lists after {@code implements}. */
  static List<String> implemented(final SyntaxNode clazz) {
    final List<SyntaxNode> parts = clazz.children();
    for (int index = 0; index < parts.size() - 1; index++) {
      if (IMPLEMENTS.equals(parts.get(index).kind())) {
        return JavaNodes.kids(parts.get(index + 1), TYPE_TYPE).stream()
            .map(type -> simpleName(SyntaxWalk.text(type)))
            .toList();
      }
    }
    return List.of();
  }

  /** Returns the simple name of the class a class extends, when it extends one. */
  static Optional<String> superclass(final SyntaxNode clazz) {
    final List<SyntaxNode> parts = clazz.children();
    for (int index = 0; index < parts.size() - 1; index++) {
      if (EXTENDS.equals(parts.get(index).kind())) {
        return Optional.of(simpleName(SyntaxWalk.text(parts.get(index + 1))));
      }
    }
    return Optional.empty();
  }
}
