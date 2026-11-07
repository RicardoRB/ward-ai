package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.fileMatches;
import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavadocAnnotationCheckTest {

  private static final String MESSAGE = "message";
  private static final String TAG = "@deprecated";
  private static final String ANNOTATION = "@Deprecated";
  private static final String PUBLIC = "public";
  private static final String JAVADOC = "/** Old. @deprecated */\n";
  private static final int MEMBER_OFFSET = JAVADOC.length();
  private static final int RUN_COLUMN = 40;

  @Test
  void reportsAMemberWithTheTagAndWithoutTheAnnotation() {
    assertThat(run(false, JAVADOC, member(PUBLIC))).hasSize(1);
    assertThat(run(false, JAVADOC, member(PUBLIC, annotation(ANNOTATION)))).isEmpty();
  }

  @Test
  void ignoresMembersWithoutTheTagOrJavadocBeforeThem() {
    assertThat(run(false, "/** Old. */\n", member(PUBLIC))).isEmpty();
    assertThat(run(false, "int x;\n", member(PUBLIC))).isEmpty();
    assertThat(run(false, "/* not javadoc @deprecated */\n", member(PUBLIC))).isEmpty();
    assertThat(run(false, "x", member(PUBLIC))).isEmpty();
  }

  @Test
  void checksOnlyMethodsWhenMethodsOnly() {
    assertThat(run(true, JAVADOC, member(PUBLIC))).isEmpty();
    assertThat(run(true, JAVADOC, methodMember(PUBLIC))).hasSize(1);
    assertThat(run(false, JAVADOC, member(PUBLIC))).hasSize(1);
  }

  private static List<RuleMatch> run(
      final boolean methodsOnly, final String text, final SyntaxNode member) {
    return fileMatches(
        new JavadocAnnotationCheck(TAG, ANNOTATION, methodsOnly, MESSAGE),
        text,
        group("compilationUnit", member));
  }

  private static SyntaxNode member(final String modifier, final SyntaxNode... extra) {
    final SyntaxNode[] children = new SyntaxNode[extra.length + 1];
    children[0] = tok("modifier", modifier, 2, MEMBER_OFFSET);
    System.arraycopy(extra, 0, children, 1, extra.length);
    return group("classBodyDeclaration", children);
  }

  private static SyntaxNode methodMember(final String modifier) {
    return group(
        "classBodyDeclaration",
        tok("modifier", modifier, 2, MEMBER_OFFSET),
        group(
            "memberDeclaration",
            group("methodDeclaration", tok("IDENTIFIER", "run", 2, RUN_COLUMN))));
  }

  private static SyntaxNode annotation(final String text) {
    return group("modifier", tok("annotation", text, 2, MEMBER_OFFSET + 1));
  }
}
