package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The fields that a Java class declares, which the bodies of the class can call through: their
 * written type, whether they are final, and their initializer.
 */
final class JavaClassFields {

  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classBodyDeclaration";
  private static final String FIELD = "fieldDeclaration";
  private static final String MODIFIER = "modifier";
  private static final String FINAL = "final";
  private static final String TYPE_TYPE = "typeType";
  private static final String DECLARATORS = "variableDeclarators";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String INITIALIZER = "variableInitializer";
  private static final String THIS = "this.";

  /**
   * One declared field.
   *
   * @param name the field name
   * @param type the type as written
   * @param isFinal whether the field is final, so only its initializer assigns it
   * @param initializer the initializer node, or null
   */
  record Field(String name, String type, boolean isFinal, SyntaxNode initializer) {

    /** Gives a body the type of the field, and its creation when a final field is initialized. */
    void declareIn(final Map<String, String> declared, final JavaCreationTypes creations) {
      declared.put(name, type);
      declared.put(THIS + name, type);
      if (isFinal && initializer != null) {
        creations.field(name, initializer);
      }
    }
  }

  private JavaClassFields() {}

  /** The fields of the classes around a type, then those of the type itself. */
  static List<Field> merged(final List<Field> outer, final SyntaxNode type) {
    final List<Field> fields = new ArrayList<>(outer);
    fields.addAll(of(type));
    return fields;
  }

  /** Reads the fields of a class declaration's body; other type declarations have none. */
  static List<Field> of(final SyntaxNode type) {
    final List<Field> found = new ArrayList<>();
    final SyntaxNode body = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(type), CLASS_BODY);
    if (body != null) {
      JavaFlowSyntax.allOf(JavaFlowSyntax.parts(body), MEMBER).forEach(m -> member(m, found));
    }
    return found;
  }

  private static void member(final SyntaxNode member, final List<Field> found) {
    final SyntaxNode field = descendant(member, FIELD, 2);
    if (field == null) {
      return;
    }
    final boolean isFinal =
        JavaFlowSyntax.allOf(JavaFlowSyntax.parts(member), MODIFIER).stream()
            .anyMatch(modifier -> FINAL.equals(JavaFlowSyntax.text(modifier)));
    final SyntaxNode type = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(field), TYPE_TYPE);
    final SyntaxNode declarators = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(field), DECLARATORS);
    if (type == null || declarators == null) {
      return;
    }
    for (final SyntaxNode declarator :
        JavaFlowSyntax.allOf(JavaFlowSyntax.parts(declarators), DECLARATOR)) {
      final SyntaxNode id = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(declarator), DECLARATOR_ID);
      if (id != null) {
        found.add(
            new Field(
                JavaFlowSyntax.text(JavaSyntaxNames.identifierOf(id)),
                JavaFlowSyntax.text(type),
                isFinal,
                JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(declarator), INITIALIZER)));
      }
    }
  }

  private static SyntaxNode descendant(final SyntaxNode node, final String kind, final int depth) {
    for (final SyntaxNode child : JavaFlowSyntax.parts(node)) {
      if (kind.equals(child.kind())) {
        return child;
      }
      final SyntaxNode deeper = depth > 1 ? descendant(child, kind, depth - 1) : null;
      if (deeper != null) {
        return deeper;
      }
    }
    return null;
  }
}
