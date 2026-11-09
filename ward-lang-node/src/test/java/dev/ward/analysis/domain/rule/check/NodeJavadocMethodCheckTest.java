package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the javadoc method check on parsed TypeScript. */
class NodeJavadocMethodCheckTest {

  private static final String DOC =
      "/**\n * Adds.\n * @param a first\n * @param b second\n * @return the sum\n */\n";

  @Test
  void matchingTagsAreNotReported() {
    assertThat(
            messages(DOC + "export function add(a: number, b: number): number { return a + b; }\n"))
        .isEmpty();
  }

  @Test
  void missingParamTagIsReported() {
    assertThat(
            messages(
                "/**\n * Adds.\n * @param a first\n * @return the sum\n */\nexport function add(a: number, b: number): number { return a + b; }\n"))
        .containsExactly("expected @param tag for 'b'");
  }

  @Test
  void unusedParamTagIsReported() {
    assertThat(
            messages(
                DOC.replace("@param b second", "@param b second\n * @param c extra")
                    + "export function add(a: number, b: number): number { return a + b; }\n"))
        .containsExactly("unused @param tag for 'c'");
  }

  @Test
  void missingReturnTagOnValueFunctionIsReported() {
    assertThat(
            messages(
                "/**\n * Adds.\n * @param a first\n * @param b second\n */\nexport function add(a: number, b: number): number { return a + b; }\n"))
        .containsExactly("expected @return tag");
  }

  @Test
  void voidFunctionNeedsNoReturnTag() {
    assertThat(
            messages(
                "/**\n * Logs.\n * @param a first\n */\nexport function log(a: number): void { void a; }\n"))
        .isEmpty();
  }

  private static List<String> messages(final String source) {
    final SyntaxNode root =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source))
            .tree();
    return new NodeJavadocMethodCheck(false, false, "{value}")
        .matches(root).stream().map(RuleMatch::message).toList();
  }
}
