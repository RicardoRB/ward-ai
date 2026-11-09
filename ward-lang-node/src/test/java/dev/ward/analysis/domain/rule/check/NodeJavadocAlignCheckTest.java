package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the javadoc alignment check on parsed JavaScript. */
class NodeJavadocAlignCheckTest {

  @Test
  void asteriskOneColumnRightOfTheSlashIsAligned() {
    assertThat(run("/**\n * Adds.\n */\nexport function f() {}\n")).isEmpty();
  }

  @Test
  void misalignedAsteriskIsReportedAtTheComment() {
    final List<RuleMatch> found = run("/**\n * Adds.\n   * More.\n */\nexport function f() {}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(1);
  }

  private static List<RuleMatch> run(final String source) {
    final SyntaxNode root =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("m", AnalysisLanguage.JAVASCRIPT, source))
            .tree();
    return new NodeJavadocAlignCheck("aligned").matches(root);
  }
}
