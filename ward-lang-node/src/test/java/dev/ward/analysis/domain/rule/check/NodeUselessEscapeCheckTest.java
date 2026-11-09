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

/** Reports an escape that a string, template or regular expression does not need. */
class NodeUselessEscapeCheckTest {

  private static final String MESSAGE = "unnecessary escape character: \\{value}";
  private static final int COLUMN = 12;

  /** Scenario: an escaped letter in a string is reported at its backslash. */
  @Test
  void aUselessEscapeInAStringIsReportedAtItsBackslash() {
    final List<RuleMatch> found = run("const a = \"\\a\";\n", Set.of());

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startColumn()).isEqualTo(COLUMN);
    assertThat(found.get(0).message()).isEqualTo("unnecessary escape character: \\a");
  }

  /** Scenario: a regex escape of a character that needs none is reported outside a class. */
  @Test
  void aRegexEscapeOutsideAClassIsReported() {
    assertThat(run("const r = /\\-/;\n", Set.of())).hasSize(1);
  }

  /** Scenario: the allowed regex character keeps its escape outside a class. */
  @Test
  void anAllowedRegexCharacterIsNotReported() {
    assertThat(run("const r = /\\-/;\n", Set.of("-"))).isEmpty();
  }

  /** Scenario: a valid escape in each context is not reported. */
  @Test
  void validEscapesAreNotReported() {
    assertThat(
            run("const a = \"\\n\\\\\\\"\";\nconst b = `\\x`;\nconst c = /[\\]\\-]/;\n", Set.of()))
        .isEmpty();
  }

  private static List<RuleMatch> run(final String source, final Set<String> allowed) {
    final SourceUnit unit = new SourceUnit("a", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeUselessEscapeCheck(allowed, MESSAGE).matches(root);
  }
}
