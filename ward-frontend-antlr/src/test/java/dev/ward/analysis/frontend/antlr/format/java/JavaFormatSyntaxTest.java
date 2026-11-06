package dev.ward.analysis.frontend.antlr.format.java;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatSyntaxTree;
import dev.ward.analysis.domain.format.FormatToken;
import org.junit.jupiter.api.Test;

class JavaFormatSyntaxTest {

  private static final String CLEAN_SOURCE = "class A {\n  // note\n  int x; /* y */\n}\n";

  @Test
  void keepsCommentsAndDropsWhitespace() {
    final FormatSyntaxTree tree = new JavaFormatSyntax().parse(CLEAN_SOURCE);

    assertThat(tree.errorLine()).isEmpty();
    assertThat(tree.tokens())
        .extracting(FormatToken::type)
        .containsSubsequence("comment", "comment")
        .doesNotContain("WS");
    assertThat(tree.tokens())
        .filteredOn(token -> "comment".equals(token.type()))
        .extracting(FormatToken::text)
        .containsExactly("// note", "/* y */");
  }

  @Test
  void reportsTheFirstSyntaxErrorLine() {
    final FormatSyntaxTree tree = new JavaFormatSyntax().parse("class A {\n  int x = ;\n}\n");

    assertThat(tree.errorLine()).hasValue(2);
  }

  @Test
  void listsRuleKindsInPreOrder() {
    final FormatSyntaxTree tree = new JavaFormatSyntax().parse(CLEAN_SOURCE);

    assertThat(tree.rules().getFirst().kind()).isEqualTo("compilationunit");
  }
}
