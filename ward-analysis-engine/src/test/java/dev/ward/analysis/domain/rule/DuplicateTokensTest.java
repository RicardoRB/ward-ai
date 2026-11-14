package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.DuplicateTokens.Token;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DuplicateTokensTest {

  private static final DuplicateCodeOptions KEEP = new DuplicateCodeOptions(1, false, false, false);
  private static final DuplicateCodeOptions ABSTRACT =
      new DuplicateCodeOptions(1, true, true, true);
  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void everyLanguageYieldsOneTokenPerTextLeaf() {
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      final List<Token> tokens = DuplicateTokens.of(leaf("any", "x"), language, KEEP);

      assertThat(tokens).as(language.name()).hasSize(1);
    }
  }

  @Test
  void javaIdentifiersAndLiteralsCompareEqualWhenTheOptionsIgnoreThem() {
    final List<Token> first =
        tokens(AnalysisLanguage.JAVA, "IDENTIFIER", "a", "STRING_LITERAL", "\"x\"");
    final List<Token> second =
        tokens(AnalysisLanguage.JAVA, "IDENTIFIER", "b", "STRING_LITERAL", "\"y\"");

    assertThat(first)
        .extracting(Token::value)
        .isEqualTo(second.stream().map(Token::value).toList());
  }

  @Test
  void keepsTheTextWhenTheOptionsDoNotIgnoreIt() {
    final Token a = DuplicateTokens.of(leaf("IDENTIFIER", "a"), AnalysisLanguage.JAVA, KEEP).get(0);
    final Token b = DuplicateTokens.of(leaf("IDENTIFIER", "b"), AnalysisLanguage.JAVA, KEEP).get(0);

    assertThat(a.value()).isNotEqualTo(b.value());
  }

  private static List<Token> tokens(
      final AnalysisLanguage language,
      final String kindOne,
      final String textOne,
      final String kindTwo,
      final String textTwo) {
    final SyntaxNode root =
        new SyntaxNode(
            "root", SPAN, Map.of(), List.of(leaf(kindOne, textOne), leaf(kindTwo, textTwo)));
    return DuplicateTokens.of(root, language, ABSTRACT);
  }

  private static SyntaxNode leaf(final String kind, final String text) {
    return new SyntaxNode(kind, SPAN, Map.of("text", text), List.of());
  }
}
