package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.CorrectnessTrees.leaf;
import static dev.ward.analysis.domain.rule.check.CorrectnessTrees.node;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class InvalidRegexLiteralCheckTest {

  private static final int LITERAL = 3;
  private static final int CLOSE = 4;

  private static final InvalidRegexLiteralCheck CHECK = new InvalidRegexLiteralCheck("message");

  private static SyntaxNode call(final String name, final String argument) {
    return node(
        "methodCall",
        1,
        leaf("IDENTIFIER", name, 1),
        leaf("(", "(", 2),
        leaf("STRING", argument, LITERAL),
        leaf(")", ")", CLOSE));
  }

  @Test
  void anInvalidLiteralIsReported() {
    assertThat(CHECK.matches(call("compile", "\"(\""))).hasSize(1);
    assertThat(CHECK.matches(call("split", "\"[a-\""))).hasSize(1);
  }

  @Test
  void aValidLiteralIsNotReported() {
    assertThat(CHECK.matches(call("compile", "\"\\\\d+\""))).isEmpty();
    assertThat(CHECK.matches(call("matches", "\"a\\tb\\n\\\"\""))).isEmpty();
    assertThat(CHECK.matches(call("replaceAll", "\"\\\\.\""))).isEmpty();
  }

  @Test
  void aLiteralEndingInAnEscapedBackslashIsInvalid() {
    assertThat(CHECK.matches(call("compile", "\"a\\\\\""))).hasSize(1);
  }

  @Test
  void otherMethodsAndNonLiteralsAreNotChecked() {
    assertThat(CHECK.matches(call("write", "\"(\""))).isEmpty();
    assertThat(CHECK.matches(call("compile", "pattern"))).isEmpty();
  }
}
