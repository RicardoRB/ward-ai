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

class BiomeBlankTargetCheckTest {

  private static final BiomeBlankTargetCheck CHECK =
      new BiomeBlankTargetCheck(Set.of("x.com"), false, "message");

  @Test
  void reportsAnExternalLinkWithoutNoopenerAtTheTarget() {
    assertThat(reported("const a = <a href=\"https://y.com\" target=\"_blank\" />;\n"))
        .containsExactly("target=\"_blank\"");
  }

  @Test
  void exemptsAllowedDomainsAndTheirSubdomains() {
    assertThat(reported("const a = <a href=\"https://x.com\" target=\"_blank\" />;\n")).isEmpty();
    assertThat(reported("const a = <a href=\"https://d.x.com/p\" target=\"_blank\" />;\n"))
        .isEmpty();
  }

  @Test
  void keepsNoreferrerOnlyWhenTheOptionDoesNotAllowIt() {
    final String source =
        "const a = <a href=\"https://y.com\" target=\"_blank\" rel=\"noreferrer\" />;\n";
    assertThat(reported(source)).containsExactly("target=\"_blank\"");
    assertThat(new BiomeBlankTargetCheck(Set.of(), true, "message").matches(parse(source)))
        .isEmpty();
  }

  private static List<String> reported(final String source) {
    return CHECK.matches(parse(source)).stream()
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
