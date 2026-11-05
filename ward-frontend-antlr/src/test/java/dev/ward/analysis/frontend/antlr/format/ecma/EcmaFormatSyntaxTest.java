package dev.ward.analysis.frontend.antlr.format.ecma;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatSyntaxTree;
import dev.ward.analysis.domain.format.FormatToken;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Checks the tokens and error lines the safety checks read from the parsers. */
class EcmaFormatSyntaxTest {

  @Test
  void commentsAreTypedAsComments() {
    final FormatSyntaxTree tree =
        new TypeScriptFormatSyntax().parse("const a = 1; // note\n/* block */\n");

    assertThat(tree.tokens())
        .extracting(FormatToken::type)
        .contains("comment")
        .doesNotContain("WhiteSpaces", "LineTerminator");
    assertThat(tree.errorLine()).isEqualTo(OptionalInt.empty());
  }

  @Test
  void syntaxErrorLineIsTheFirstErrorLine() {
    final FormatSyntaxTree tree =
        new JavaScriptFormatSyntax().parse("const a = 1;\nfunction broken(a {\n}\n");

    assertThat(tree.errorLine()).isEqualTo(OptionalInt.of(2));
  }

  @Test
  void ruleKindsAreListedInPreOrder() {
    final FormatSyntaxTree tree = new JavaScriptFormatSyntax().parse("a;\n");

    assertThat(tree.rules().getFirst().kind()).isEqualTo("program");
  }
}
