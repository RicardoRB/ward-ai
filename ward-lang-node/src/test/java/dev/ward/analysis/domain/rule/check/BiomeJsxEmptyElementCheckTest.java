package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BiomeJsxEmptyElementCheckTest {

  private static final BiomeJsxEmptyElementCheck ANCHOR =
      new BiomeJsxEmptyElementCheck(Set.of("a"), "message");

  @Test
  void reportsASelfClosingOrBlankAnchorAtTheElement() {
    assertThat(reported("const a = <a />;\n")).containsExactly("<a />");
    assertThat(reported("const a = <a>   </a>;\n")).containsExactly("<a>   </a>");
  }

  @Test
  void staysSilentForTextAnExpressionOrAChildElement() {
    assertThat(reported("const a = <a>x</a>;\n")).isEmpty();
    assertThat(reported("const a = <a>{children}</a>;\n")).isEmpty();
    assertThat(reported("const a = <a><span /></a>;\n")).isEmpty();
    assertThat(reported("const a = <div />;\n")).isEmpty();
  }

  private static List<String> reported(final String source) {
    final SyntaxNode root = parse(source);
    return ANCHOR.matches(root).stream()
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
