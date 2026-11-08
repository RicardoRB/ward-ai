package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads what a class declaration says about its name, superclass and interfaces. */
final class ClassHeaders {

  private static final String EXTENDS = "'extends'";
  private static final String IMPLEMENTS = "'implements'";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_LIST = "typeList";
  private static final String TYPE_TYPE = "typeType";
  private static final String TYPE_PARAMETERS = "typeParameters";
  private static final Set<String> SERIALIZABLE = Set.of("Serializable", "Externalizable");
  private static final Pattern GENERICS = Pattern.compile("<.*>");

  private ClassHeaders() {}

  /** Returns the name of a class declaration. */
  static String name(final SyntaxNode clazz) {
    return clazz.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  /** Returns the text of the superclass, when the class names one. */
  static Optional<String> extendsType(final SyntaxNode clazz) {
    final List<SyntaxNode> parts = clazz.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (EXTENDS.equals(parts.get(index).kind())) {
        return Optional.of(SyntaxWalk.text(parts.get(index + 1)));
      }
    }
    return Optional.empty();
  }

  /** Returns the simple names of the interfaces the class implements. */
  static List<String> implemented(final SyntaxNode clazz) {
    final List<String> names = new ArrayList<>();
    final List<SyntaxNode> parts = clazz.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (IMPLEMENTS.equals(parts.get(index).kind())
          && TYPE_LIST.equals(parts.get(index + 1).kind())) {
        parts.get(index + 1).children().stream()
            .filter(child -> TYPE_TYPE.equals(child.kind()))
            .forEach(child -> names.add(simple(SyntaxWalk.text(child))));
      }
    }
    return names;
  }

  /** Returns whether the class names Serializable or Externalizable among its interfaces. */
  static boolean implementsSerializable(final SyntaxNode clazz) {
    return implemented(clazz).stream().anyMatch(SERIALIZABLE::contains);
  }

  /** Returns whether the class declares type parameters. */
  static boolean isGeneric(final SyntaxNode clazz) {
    return clazz.children().stream().anyMatch(child -> TYPE_PARAMETERS.equals(child.kind()));
  }

  /** Returns the simple name of a type text: {@code List} for {@code java.util.List<String>}. */
  static String simple(final String type) {
    final String bare = GENERICS.matcher(type).replaceAll("");
    return bare.substring(bare.lastIndexOf('.') + 1);
  }
}
