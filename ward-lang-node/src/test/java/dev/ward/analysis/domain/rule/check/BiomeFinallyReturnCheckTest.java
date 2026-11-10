package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class BiomeFinallyReturnCheckTest {

  private static final BiomeFinallyReturnCheck CHECK = new BiomeFinallyReturnCheck("message");

  @Test
  void reportsAReturnInsideAFinallyCallback() {
    assertThat(reported(CHECK, "p.finally(() => { return 2; });\n")).containsExactly("return 2;");
  }

  @Test
  void ignoresAReturnInANestedFunctionAndOtherCalls() {
    assertThat(reported(CHECK, "p.finally(function () { function n() { return 2; } });\n"))
        .isEmpty();
    assertThat(reported(CHECK, "p.then(() => { return 2; });\n")).isEmpty();
  }

  private static List<String> reported(final RuleCheck check, final String source) {
    final SyntaxNode root = parse(source);
    return check.matches(root).stream()
        .map(RuleMatch::span)
        .map(span -> source.substring(span.startOffset(), span.endOffset()))
        .toList();
  }

  private static SyntaxNode parse(final String source) {
    final SourceUnit unit = new SourceUnit("src/a.ts", AnalysisLanguage.TYPESCRIPT, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    return parsed.tree();
  }
}
