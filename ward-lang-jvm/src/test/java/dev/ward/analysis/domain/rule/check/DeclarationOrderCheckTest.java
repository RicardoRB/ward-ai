package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The declaration-order check on small class bodies: groups, access ranks and static variables. */
class DeclarationOrderCheckTest extends TreeBuilders {

  private static final String MESSAGE = "out of order";
  private static final int MODIFIER_OFFSET = 0;
  private static final int MEMBER_OFFSET = 10;
  private static final int MEMBER_LENGTH = 10;
  private static final int FIELD_NAME_OFFSET = 4;
  private static final int METHOD_NAME_OFFSET = 5;
  private static final int OPEN_OFFSET = 9;
  private static final int CLOSE_OFFSET = 10;

  @Test
  void methodBeforeInstanceVariableIsReported() {
    assertThat(findings(classBody(method("run"), field(null, "count")))).hasSize(1);
  }

  @Test
  void staticVariableAfterInstanceVariableIsReported() {
    assertThat(findings(classBody(field(null, "count"), field("static", "total")))).hasSize(1);
  }

  @Test
  void publicVariableAfterPrivateVariableIsReported() {
    assertThat(findings(classBody(field("private", "count"), field("public", "size")))).hasSize(1);
  }

  @Test
  void correctOrderIsNotReported() {
    final SyntaxNode body =
        classBody(
            field("static", "total"),
            field("public", "size"),
            field("private", "count"),
            method("run"));
    assertThat(findings(body)).isEmpty();
  }

  private static List<RuleMatch> findings(final SyntaxNode body) {
    return new DeclarationOrderCheck(MESSAGE).check(unit(body), FILE);
  }

  private static SyntaxNode classBody(final SyntaxNode... members) {
    return n(CLASS_BODY, members);
  }

  private static SyntaxNode field(final String modifier, final String name) {
    return member(
        modifier,
        Trees.node(
            MEMBER_DECLARATION,
            MEMBER_OFFSET,
            MEMBER_OFFSET + MEMBER_LENGTH,
            1,
            n(
                "fieldDeclaration",
                Trees.leaf("int", "int", MEMBER_OFFSET),
                n(
                    "variableDeclarator",
                    Trees.leaf(TOKEN_IDENTIFIER, name, MEMBER_OFFSET + FIELD_NAME_OFFSET)))));
  }

  private static SyntaxNode method(final String name) {
    return member(
        null,
        Trees.node(
            MEMBER_DECLARATION,
            MEMBER_OFFSET,
            MEMBER_OFFSET + MEMBER_LENGTH,
            1,
            n(
                METHOD_DECLARATION,
                Trees.leaf("void", "void", MEMBER_OFFSET),
                Trees.leaf(TOKEN_IDENTIFIER, name, MEMBER_OFFSET + METHOD_NAME_OFFSET),
                Trees.leaf(TOKEN_OPEN, "(", MEMBER_OFFSET + OPEN_OFFSET),
                Trees.leaf(TOKEN_CLOSE, ")", MEMBER_OFFSET + CLOSE_OFFSET))));
  }

  private static SyntaxNode member(final String modifier, final SyntaxNode declaration) {
    if (modifier == null) {
      return n(CLASS_BODY_DECLARATION, declaration);
    }
    return n(
        CLASS_BODY_DECLARATION,
        Trees.leaf("'" + modifier + "'", modifier, MODIFIER_OFFSET),
        declaration);
  }
}
