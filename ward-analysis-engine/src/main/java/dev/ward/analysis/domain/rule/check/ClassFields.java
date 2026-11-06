package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the field names of a class body, and which of them have an array type. */
final class ClassFields {

  private static final String FIELD_DECLARATION = "fieldDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String DECLARATOR_ID = "variableDeclaratorId";

  private ClassFields() {}

  /** Returns the names of every field of the class body. */
  static Set<String> names(final SyntaxNode classBody) {
    return declarations(classBody).stream()
        .flatMap(field -> declaredNames(field).stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Returns the names of the fields whose declaration has an array type. */
  static Set<String> arrayNames(final SyntaxNode classBody) {
    return declarations(classBody).stream()
        .filter(field -> SyntaxWalk.text(field).contains("[]"))
        .flatMap(field -> declaredNames(field).stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static List<SyntaxNode> declarations(final SyntaxNode classBody) {
    return JUnitTests.members(classBody).stream()
        .flatMap(member -> member.children().stream())
        .filter(child -> MEMBER_DECLARATION.equals(child.kind()))
        .flatMap(member -> member.children().stream())
        .filter(child -> FIELD_DECLARATION.equals(child.kind()))
        .toList();
  }

  private static List<String> declaredNames(final SyntaxNode field) {
    return SyntaxWalk.descendants(field).stream()
        .filter(node -> DECLARATOR_ID.equals(node.kind()))
        .flatMap(id -> id.children().stream())
        .filter(child -> "identifier".equals(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }
}
