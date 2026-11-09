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

class BiomeJsxRequiredAttributeCheckTest {

  private static final BiomeJsxRequiredAttributeCheck BUTTON =
      new BiomeJsxRequiredAttributeCheck(
          Set.of("button"), "type", Set.of("button", "submit", "reset"), true, "message");

  @Test
  void reportsAMissingAttributeAtTheElementAndAnUnknownValueAtTheValue() {
    assertThat(reported(BUTTON, "const a = <button>x</button>;\n")).containsExactly("<button>");
    assertThat(reported(BUTTON, "const a = <button type=\"bogus\" />;\n"))
        .containsExactly("\"bogus\"");
  }

  @Test
  void staysSilentForAStaticValueAGuessAndASpreadThatMaySupplyIt() {
    assertThat(reported(BUTTON, "const a = <button type=\"submit\" />;\n")).isEmpty();
    assertThat(reported(BUTTON, "const a = <button type={t} />;\n")).isEmpty();
    assertThat(reported(BUTTON, "const a = <button {...p} />;\n")).isEmpty();
    assertThat(reported(BUTTON, "const a = <Button />;\n")).isEmpty();
  }

  @Test
  void checksAnElementOnlyWhenItsStaticAttributeMatches() {
    final BiomeJsxRequiredAttributeCheck image =
        new BiomeJsxRequiredAttributeCheck(
            Set.of("input[type=image]"), "alt", Set.of(), false, "message");
    assertThat(reported(image, "const a = <input type=\"image\" src=\"x\" />;\n"))
        .containsExactly("<input type=\"image\" src=\"x\" />");
    assertThat(reported(image, "const a = <input type=\"text\" />;\n")).isEmpty();
    assertThat(reported(image, "const a = <input type={t} />;\n")).isEmpty();
  }

  private static List<String> reported(
      final BiomeJsxRequiredAttributeCheck check, final String source) {
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
