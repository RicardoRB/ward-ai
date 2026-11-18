package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the declared type of a simple name that a method, a constructor or a class declares: its
 * parameters and locals, then the fields of the class around it. The syntax tree does not bind
 * names, so the nearest declaration of the name in the enclosing callable wins, then the class.
 */
final class DeclaredTypes {
  private static final String PARAMETER = "formalParameter";
  private static final String LOCAL = "localVariableDeclaration";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String CLASS_BODY = "classBody";

  private DeclaredTypes() {}

  /** Returns the type text of {@code name} at a node with these ancestors, or empty when unknown. */
  static String of(final String name, final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode ancestor = ancestors.get(index);
      final String type = inside(ancestor).get(name);
      if (type != null) {
        return type;
      }
    }
    return "";
  }

  private static Map<String, String> inside(final SyntaxNode ancestor) {
    final Map<String, String> types = new HashMap<>();
    final String kind = ancestor.kind();
    if (MemberDeclarations.METHOD.equals(kind) || MemberDeclarations.CONSTRUCTOR.equals(kind)) {
      for (final SyntaxNode node : SyntaxWalk.descendants(ancestor)) {
        if (PARAMETER.equals(node.kind()) || LOCAL.equals(node.kind())) {
          final String type = ClassMembers.typeText(node).orElse("");
          SyntaxWalk.descendants(node).stream()
              .filter(id -> DECLARATOR_ID.equals(id.kind()))
              .map(SyntaxWalk::text)
              .forEach(name -> types.put(name, type));
        }
      }
    } else if (CLASS_BODY.equals(kind)) {
      types.putAll(ClassFieldTypes.of(ancestor, true));
      types.putAll(ClassFieldTypes.of(ancestor, false));
    }
    return types;
  }
}
