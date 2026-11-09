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

class BiomeJsxHandlerPairCheckTest {

  private static final BiomeJsxHandlerPairCheck CLICK =
      new BiomeJsxHandlerPairCheck(
          List.of("onClick:onKeyDown|onKeyUp"), List.of("button", "a[href]"), "message");

  @Test
  void reportsAClickHandlerWithoutAKeyboardPartnerAtTheElement() {
    assertThat(reported(CLICK, "const a = <div onClick={go} />;\n"))
        .containsExactly("<div onClick={go} />");
  }

  @Test
  void staysSilentWhenAPartnerIsPresentOrTheElementIsExempt() {
    assertThat(reported(CLICK, "const a = <div onClick={go} onKeyUp={k} />;\n")).isEmpty();
    assertThat(reported(CLICK, "const a = <button onClick={go} />;\n")).isEmpty();
    assertThat(reported(CLICK, "const a = <a href=\"x\" onClick={go} />;\n")).isEmpty();
    assertThat(reported(CLICK, "const a = <Foo onClick={go} />;\n")).isEmpty();
  }

  private static List<String> reported(final BiomeJsxHandlerPairCheck check, final String source) {
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
