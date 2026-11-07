package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class NestingChecksTest {

  private static final String MESSAGE = "message";
  private static final String OPEN = "'{'";
  private static final String CLOSE = "'}'";
  private static final String STATIC = "static";
  private static final String MODIFIER = "modifier";
  private static final String BODY = "classBodyDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final int BLOCK_END = 3;

  @Test
  void avoidNestedBlocksReportsABlockInsideAStatement() {
    assertThat(nested(false, nestedBlock())).hasSize(1);
    assertThat(
            nested(
                false,
                group(
                    "statement",
                    group("block", tok(OPEN, "{", 1, 0), tok(CLOSE, "}", 1, BLOCK_END)))))
        .isEmpty();
  }

  @Test
  void avoidNestedBlocksAllowsTheOnlyStatementOfASwitchCaseWhenAsked() {
    final SyntaxNode single = group("switchBlockStatementGroup", nestedBlock());
    final SyntaxNode several = group("switchBlockStatementGroup", nestedBlock(), nestedBlock());
    assertThat(nested(true, single)).isEmpty();
    assertThat(nested(false, single)).hasSize(1);
    assertThat(nested(true, several)).hasSize(2);
  }

  @Test
  void classMemberImpliedModifierReportsANestedTypeWithoutStatic() {
    assertThat(member(true, false, false, "enumDeclaration", false)).hasSize(1);
    assertThat(member(false, true, false, "interfaceDeclaration", false)).hasSize(1);
    assertThat(member(false, false, true, "recordDeclaration", false)).hasSize(1);
    assertThat(member(true, false, false, "enumDeclaration", true)).isEmpty();
    assertThat(member(false, false, false, "enumDeclaration", false)).isEmpty();
    assertThat(member(true, false, false, "methodDeclaration", false)).isEmpty();
  }

  private static List<RuleMatch> nested(final boolean allowInSwitchCase, final SyntaxNode root) {
    return unitMatches(new AvoidNestedBlocksCheck(allowInSwitchCase, MESSAGE), root);
  }

  private static List<RuleMatch> member(
      final boolean enumFlag,
      final boolean interfaceFlag,
      final boolean recordFlag,
      final String nestedKind,
      final boolean withStatic) {
    final SyntaxNode declaration =
        group("memberDeclaration", group(nestedKind, tok(IDENTIFIER, "Inner", 1, 0)));
    final SyntaxNode decl =
        withStatic
            ? group(BODY, declaration, group(MODIFIER, tok(MODIFIER, STATIC, 1, 0)))
            : group(BODY, declaration);
    return unitMatches(
        new ClassMemberImpliedModifierCheck(enumFlag, interfaceFlag, recordFlag, MESSAGE), decl);
  }

  private static SyntaxNode nestedBlock() {
    return group(
        "blockStatement",
        group("statement", group("block", tok(OPEN, "{", 1, 0), tok(CLOSE, "}", 1, BLOCK_END))));
  }
}
