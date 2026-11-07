package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the names, types and annotations that Java declaration text holds for the design checks.
 */
final class DeclarationText {

  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String CONSTANT_DECLARATOR = "constantDeclarator";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_TYPE = "typeType";
  private static final String BLOCK_OPEN = "{";
  private static final String GENERIC_OPEN = "<";
  private static final String AT = "@";

  private DeclarationText() {}

  /** Returns the names of the variables or constants that a field declaration declares. */
  static List<String> declaredNames(final SyntaxNode field) {
    return SyntaxWalk.descendants(field).stream()
        .filter(
            node -> DECLARATOR_ID.equals(node.kind()) || CONSTANT_DECLARATOR.equals(node.kind()))
        .flatMap(node -> node.children().stream())
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }

  /** Returns the type text of a field declaration, without its generic arguments. */
  static String simpleTypeText(final SyntaxNode field) {
    return field.children().stream()
        .filter(child -> TYPE_TYPE.equals(child.kind()))
        .findFirst()
        .map(child -> simpleName(SyntaxWalk.text(child)))
        .orElse("");
  }

  /** Returns the simple name of the superclass of a class declaration, when it extends one. */
  static Optional<String> superclassName(final SyntaxNode classDeclaration) {
    return classDeclaration.children().stream()
        .filter(child -> TYPE_TYPE.equals(child.kind()))
        .findFirst()
        .map(child -> simpleName(SyntaxWalk.text(child)));
  }

  /** Returns whether a method declaration has a block body rather than a semicolon. */
  static boolean hasBody(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> "methodBody".equals(child.kind()))
        .findFirst()
        .map(child -> SyntaxWalk.text(child).startsWith(BLOCK_OPEN))
        .orElse(false);
  }

  /** Returns the simple name of a type or annotation text: no package, no generic arguments. */
  static String simpleName(final String text) {
    final String withoutGenerics = text.split(GENERIC_OPEN, 2)[0];
    return withoutGenerics.substring(withoutGenerics.lastIndexOf('.') + 1);
  }

  /** Returns the simple names of the annotations among modifier texts. */
  static Set<String> annotationNames(final List<String> modifiers) {
    return modifiers.stream()
        .filter(text -> text.startsWith(AT))
        .map(text -> text.substring(1).split("\\(", 2)[0])
        .map(DeclarationText::simpleName)
        .collect(Collectors.toUnmodifiableSet());
  }
}
