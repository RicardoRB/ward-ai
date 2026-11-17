package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Reads the field names of a class body, and which of them have an array type. */
final class ClassFields {
  private static final String FIELD_DECLARATION = "fieldDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String DECLARATOR_ID = "variableDeclaratorId";

  private static final Pattern EMPTY_ARRAY =
      Pattern.compile("=(?:new[\\w.<>?]+(?:\\[])*\\[0](?:\\[])*|new[\\w.<>?]+\\[]\\{}|\\{})[,;]?$");

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
        .filter(field -> SyntaxWalk.text(field).contains("[]") && !emptyArray(field))
        .flatMap(field -> declaredNames(field).stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Tells whether a field is initialized with a zero-length array, which nobody can change. */
  static boolean emptyArray(final SyntaxNode field) {
    return EMPTY_ARRAY.matcher(SyntaxWalk.text(field)).find();
  }

  private static List<SyntaxNode> declarations(final SyntaxNode classBody) {
    return JUnitMembers.members(classBody).stream()
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
