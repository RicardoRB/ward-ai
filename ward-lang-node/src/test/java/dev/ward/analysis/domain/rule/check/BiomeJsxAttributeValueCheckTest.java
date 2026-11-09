package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BiomeJsxAttributeValueCheckTest {

  private static final BiomeJsxAttributeValueCheck CHECK =
      new BiomeJsxAttributeValueCheck(
          "tabIndex", false, Pattern.compile("^(\\{[1-9][0-9]*\\}|\"[1-9][0-9]*\")$"), "message");

  @Test
  void reportsAPositiveValueAtTheValue() {
    assertThat(reported(CHECK, "const e = <div tabIndex={1} />;\n")).containsExactly("{1}");
  }

  @Test
  void ignoresZeroNegativeAndOtherAttributes() {
    assertThat(reported(CHECK, "const e = <div tabIndex=\"0\" />;\n")).isEmpty();
    assertThat(reported(CHECK, "const e = <div tabIndex={-1} />;\n")).isEmpty();
    assertThat(reported(CHECK, "const e = <div role={1} />;\n")).isEmpty();
  }

  private static List<String> reported(final RuleCheck check, final String source) {
    final SyntaxNode root = parse(source);
    return check.matches(root).stream()
        .map(RuleMatch::span)
        .map(span -> source.substring(span.startOffset(), span.endOffset()))
        .toList();
  }

  private static SyntaxNode parse(final String source) {
    final SourceUnit unit = new SourceUnit("src/a.jsx", AnalysisLanguage.JAVASCRIPT, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    return parsed.tree();
  }
}
