package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Builds the class members the class-level design check tests read. */
class ClassMemberTrees extends ClassTreeNodes {

  static final String PUBLIC = "public";
  static final String PRIVATE = "private";
  static final String STATIC = "static";
  static final String TRANSIENT = "transient";
  static final String INT = "int";
  static final String CLASS_NAME = "Sample";
  static final String TYPE_TYPE = "typeType";
  static final String VARIABLE_DECLARATOR_ID = "variableDeclaratorId";
  static final String FIELD_NAME = "count";
  static final String DECLARATOR = "variableDeclarator";
  static final String FORMAL_PARAMETERS = "formalParameters";
  static final String FORMAL_PARAMETER = "formalParameter";
  static final String MODIFIER = "modifier";
  static final String MEMBER_DECLARATION = "memberDeclaration";
  static final String BLOCK = "block";
  static final String VOID_TYPE = "typeTypeOrVoid";

  protected ClassMemberTrees() {
    super();
  }

  SyntaxNode classNamed(final String name, final SyntaxNode... members) {
    return node(
        "classDeclaration",
        1,
        1,
        leaf("'class'", "class", 1, 1),
        ident(name),
        node("classBody", 1, 1, members));
  }

  SyntaxNode member(final SyntaxNode declaration, final String... modifiers) {
    final List<SyntaxNode> children = new ArrayList<>();
    for (final String text : modifiers) {
      children.add(node(MODIFIER, LINE, COLUMN, leaf("'" + text + "'", text, LINE, COLUMN)));
    }
    children.add(node(MEMBER_DECLARATION, LINE, COLUMN, declaration));
    return node("classBodyDeclaration", LINE, COLUMN, children.toArray(SyntaxNode[]::new));
  }

  SyntaxNode method(
      final String name, final int parameters, final int decisions, final SyntaxNode... body) {
    final List<SyntaxNode> statements = new ArrayList<>();
    for (int index = 0; index < decisions; index++) {
      statements.add(leaf("'if'", "if", LINE, COLUMN));
    }
    statements.addAll(List.of(body));
    return node(
        "methodDeclaration",
        LINE,
        COLUMN,
        node(VOID_TYPE, LINE, COLUMN, leaf("'void'", "void", LINE, COLUMN)),
        ident(name),
        parametersOf(parameters),
        node(BLOCK, LINE, COLUMN, statements.toArray(SyntaxNode[]::new)));
  }

  SyntaxNode fieldMember() {
    return member(field(FIELD_NAME));
  }

  SyntaxNode field(final String name) {
    return node(
        "fieldDeclaration",
        LINE,
        COLUMN,
        node(TYPE_TYPE, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, INT, LINE, COLUMN)),
        node(DECLARATOR, LINE, COLUMN, node(VARIABLE_DECLARATOR_ID, LINE, COLUMN, ident(name))));
  }

  SyntaxNode typedField(final String name, final String type) {
    return node(
        "fieldDeclaration",
        LINE,
        COLUMN,
        node(TYPE_TYPE, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, type, LINE, COLUMN)),
        node(DECLARATOR, LINE, COLUMN, node(VARIABLE_DECLARATOR_ID, LINE, COLUMN, ident(name))));
  }

  SyntaxNode typedMethod(final String name, final String returns) {
    return node(
        "methodDeclaration",
        LINE,
        COLUMN,
        node(TYPE_TYPE, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, returns, LINE, COLUMN)),
        ident(name),
        node(FORMAL_PARAMETERS, LINE, COLUMN));
  }

  SyntaxNode typedSetter(final String name, final String type) {
    return node(
        "methodDeclaration",
        LINE,
        COLUMN,
        node(VOID_TYPE, LINE, COLUMN, leaf("'void'", "void", LINE, COLUMN)),
        ident(name),
        node(
            FORMAL_PARAMETERS,
            LINE,
            COLUMN,
            node(
                FORMAL_PARAMETER,
                LINE,
                COLUMN,
                node(TYPE_TYPE, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, type, LINE, COLUMN)),
                node(VARIABLE_DECLARATOR_ID, LINE, COLUMN, ident("value")))));
  }

  SyntaxNode constructorWith(final int parameters) {
    return node(
        "constructorDeclaration", LINE, COLUMN, ident(CLASS_NAME), parametersOf(parameters));
  }

  /** Parameters of type {@code int} named {@code p0}, {@code p1} and so on. */
  SyntaxNode parametersOf(final int count) {
    final List<SyntaxNode> params = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      params.add(
          node(
              FORMAL_PARAMETER,
              LINE,
              COLUMN,
              node(TYPE_TYPE, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, INT, LINE, COLUMN)),
              node(VARIABLE_DECLARATOR_ID, LINE, COLUMN, ident("p" + index))));
    }
    return node(FORMAL_PARAMETERS, LINE, COLUMN, params.toArray(SyntaxNode[]::new));
  }
}
