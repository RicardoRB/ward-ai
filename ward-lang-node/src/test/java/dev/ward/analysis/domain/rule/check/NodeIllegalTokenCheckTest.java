package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the illegal token check on parsed JavaScript. */
class NodeIllegalTokenCheckTest {

  @Test
  void noTokenKindsMeansNothingIsReported() {
    assertThat(run(Set.of(), "^'", "const s = 'x';")).isEmpty();
  }

  @Test
  void listedTokenKindWithMatchingTextIsReported() {
    assertThat(run(Set.of("StringLiteral"), "^'", "const s = 'x';")).hasSize(1);
  }

  @Test
  void tokenKindNotListedIsIgnored() {
    assertThat(run(Set.of("DecimalLiteral"), "^'", "const s = 'x';")).isEmpty();
  }

  private static List<RuleMatch> run(
      final Set<String> tokens, final String format, final String source) {
    final SyntaxNode root =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("m", AnalysisLanguage.JAVASCRIPT, source))
            .tree();
    return new NodeIllegalTokenCheck(tokens, format, false, "illegal {value}").matches(root);
  }
}
