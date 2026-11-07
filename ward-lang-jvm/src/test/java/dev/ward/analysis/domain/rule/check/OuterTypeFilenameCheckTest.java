package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The outer type filename check and the block comment asterisk check. */
class OuterTypeFilenameCheckTest extends TreeBuilders {

  private static final SourceFile FOO = new SourceFile("src/Foo.java", "");

  @Test
  void typeNamedAsTheFilePasses() {
    assertThat(types(FOO, type("Foo", false))).isEmpty();
  }

  @Test
  void firstTypeWithAnotherNameIsReported() {
    final List<RuleMatch> matches = types(FOO, type("Bar", false));
    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).message()).isEqualTo("Bar in Foo");
  }

  @Test
  void publicTypeNamedAsTheFileAfterAHelperPasses() {
    assertThat(types(FOO, type("Helper", false), type("Foo", true))).isEmpty();
  }

  @Test
  void publicTypeWithAnotherNameIsReportedInsteadOfTheFirstType() {
    assertThat(types(FOO, type("Helper", false), type("Bar", true))).hasSize(1);
  }

  @Test
  void filesWithoutTypesOrOfAnotherKindPass() {
    assertThat(types(FOO)).isEmpty();
    assertThat(types(new SourceFile("src/Foo.kt", ""), type("Bar", false))).isEmpty();
  }

  @Test
  void rowsOfABlockCommentWithoutAnAsteriskAreReported() {
    assertThat(block("/*\n   * Fine.\n   */")).isEmpty();
    assertThat(block("/*\n   Missing.\n   */")).hasSize(1);
    assertThat(block("/*\n\n   */")).hasSize(1);
    assertThat(block("/** Javadoc.\n   Text.\n   */")).isEmpty();
    assertThat(block("// one row")).isEmpty();
  }

  private static SyntaxNode type(final String name, final boolean isPublic) {
    final SyntaxNode declaration = n("classDeclaration", t("'class'", "class"), id(name));
    return isPublic
        ? n("typeDeclaration", n("classOrInterfaceModifier", t(PUBLIC, PUBLIC)), declaration)
        : n("typeDeclaration", declaration);
  }

  private static List<RuleMatch> types(final SourceFile file, final SyntaxNode... types) {
    return new OuterTypeFilenameCheck("{value} in {max}").check(n("compilationUnit", types), file);
  }

  private static List<RuleMatch> block(final String text) {
    return new BlockCommentAsteriskCheck(MESSAGE).check(unit(Trees.leaf("comment", text, 2)), FILE);
  }
}
