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

class BiomeAutofocusCheckTest {

  private static final BiomeAutofocusCheck CHECK = new BiomeAutofocusCheck("message");

  @Test
  void reportsADomElementOutsideADialogOrPopover() {
    assertThat(reported("const a = <div><input autoFocus /></div>;\n")).containsExactly("autoFocus");
  }

  @Test
  void exemptsComponentsDialogsAndPopoversAndTheirDescendants() {
    assertThat(reported("const a = <Input autoFocus />;\n")).isEmpty();
    assertThat(reported("const a = <ui.Input autoFocus />;\n")).isEmpty();
    assertThat(reported("const a = <DIALOG><input autoFocus /></DIALOG>;\n")).isEmpty();
    assertThat(reported("const a = <div popover><input autoFocus /></div>;\n")).isEmpty();
  }

  private static List<String> reported(final String source) {
    final SyntaxNode root = parse(source);
    return CHECK.matches(root).stream().map(RuleMatch::span)
        .map(span -> source.substring(span.startOffset(), span.endOffset()))
        .toList();
  }

  private static SyntaxNode parse(final String source) {
    final SourceUnit unit = new SourceUnit("src/a.jsx", AnalysisLanguage.JAVASCRIPT, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    return parsed.tree();
  }
}
