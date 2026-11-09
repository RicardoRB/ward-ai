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

class BiomeDuplicatePropsCheckTest {

  private static final BiomeDuplicatePropsCheck CHECK = new BiomeDuplicatePropsCheck("message");

  @Test
  void reportsTheFirstOfEachRepeatedNameOnce() {
    assertThat(reported("const a = <div id=\"a\" id=\"b\" id=\"c\" />;\n"))
        .containsExactly("id=\"a\"");
  }

  @Test
  void comparesNamesAsWrittenAndIgnoresSpreadsAndNamespaces() {
    assertThat(reported("const a = <div id=\"a\" ID=\"b\" {...p} />;\n")).isEmpty();
    assertThat(reported("const a = <svg xlink:href=\"a\" xlink:title=\"b\" />;\n")).isEmpty();
  }

  private static List<String> reported(final String source) {
    final SyntaxNode root = parse(source);
    return CHECK.matches(root).stream()
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
