package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The checks that read comments: unused imports and missing documentation. */
class DocumentChecksTest {

  private static final String DECLARATION_KIND = "methodDeclaration";

  @Test
  void unreferencedNameReportsAnImportNoOneUses() {
    final SyntaxNode importUnused = Trees.importNode("java.util.Map", 0);
    final SyntaxNode importUsed = Trees.importNode("java.util.List", 30);
    final SyntaxNode use = Trees.leaf(Trees.IDENTIFIER, "List", 60);
    final SyntaxNode unit = Trees.node("compilationUnit", 0, 70, 3, importUnused, importUsed, use);

    assertThat(Trees.messages(new UnreferencedNameCheck("unused {value}").matches(unit)))
        .containsExactly("unused Map");
  }

  @Test
  void unreferencedNameCountsAJavadocReference() {
    final SyntaxNode importNode = Trees.importNode("java.util.List", 0);
    final SyntaxNode javadoc = Trees.leaf(Trees.COMMENT, "/** See {@link List} */", 40);
    final SyntaxNode unit = Trees.node("compilationUnit", 0, 70, 3, importNode, javadoc);

    assertThat(new UnreferencedNameCheck("unused {value}").matches(unit)).isEmpty();
  }

  @Test
  void precedingCommentAcceptsADocumentedDeclaration() {
    assertThat(preceding(Optional.empty()).matches(Trees.documentedClass(true))).isEmpty();
  }

  @Test
  void precedingCommentReportsAnUndocumentedDeclaration() {
    assertThat(Trees.messages(preceding(Optional.empty()).matches(Trees.documentedClass(false))))
        .containsExactly("missing");
  }

  @Test
  void precedingCommentSkipsAnExemptAnnotation() {
    final SyntaxNode overriding =
        Trees.node(
            "classBodyDeclaration",
            0,
            30,
            1,
            Trees.node(
                "modifier",
                0,
                9,
                1,
                Trees.node(
                    "annotation",
                    0,
                    9,
                    1,
                    Trees.leaf("'@'", "@", 0),
                    Trees.leaf(Trees.IDENTIFIER, "Override", 1))),
            Trees.node(DECLARATION_KIND, 12, 30, 1, Trees.leaf(Trees.IDENTIFIER, "run", 20)));
    final PrecedingCommentCheck check =
        new PrecedingCommentCheck(
            Trees.filter(Set.of(DECLARATION_KIND), Optional.empty(), Optional.empty()),
            Optional.of("Override"),
            "missing");

    assertThat(check.matches(Trees.unit(overriding))).isEmpty();
  }

  private static PrecedingCommentCheck preceding(final Optional<String> exempt) {
    return new PrecedingCommentCheck(
        Trees.filter(Set.of("classDeclaration"), Optional.of("public"), Optional.empty()),
        exempt,
        "missing");
  }
}
