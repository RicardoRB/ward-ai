package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BiomeNoRedundantRolesCheckTest {

  private static final AriaRoleTable TABLE =
      new AriaRoleTable(
          Map.of(
              "banner",
              new AriaRoleTable.AriaRoleRow(
                  "banner", false, Set.of(), false, true, true, false, Set.of(), Set.of(), Set.of(),
                  Set.of()),
              "navigation",
              new AriaRoleTable.AriaRoleRow(
                  "navigation",
                  false,
                  Set.of(),
                  false,
                  true,
                  true,
                  false,
                  Set.of(),
                  Set.of(),
                  Set.of(),
                  Set.of())),
          AriaRoleTable.parseImplicit(
              List.of("nav\t-\tnavigation", "div\t-\tgeneric", "img\talt-blank\tpresentation")),
          Map.of());
  private static final BiomeNoRedundantRolesCheck CHECK =
      new BiomeNoRedundantRolesCheck(TABLE, "message");

  @Test
  void reportsARoleThatRepeatsTheImplicitRoleAtTheValue() {
    assertThat(reported("const a = <nav role=\"navigation\" />;\n"))
        .containsExactly("\"navigation\"");
  }

  @Test
  void readsTheFirstConcreteTokenOfAMultiTokenRole() {
    assertThat(reported("const a = <nav role=\"banner navigation\" />;\n")).isEmpty();
    assertThat(reported("const a = <nav role=\"navigation banner\" />;\n"))
        .containsExactly("\"navigation banner\"");
  }

  @Test
  void staysSilentForADifferentRoleAnUnknownElementOrANonConcreteRole() {
    assertThat(reported("const a = <nav role=\"banner\" />;\n")).isEmpty();
    assertThat(reported("const a = <Nav role=\"navigation\" />;\n")).isEmpty();
    assertThat(reported("const a = <section role=\"navigation\" />;\n")).isEmpty();
  }

  private static List<String> reported(final String source) {
    final SourceUnit unit = new SourceUnit("src/a.jsx", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return CHECK.matches(root, FlowView.NONE, unit).stream()
        .map(RuleMatch::span)
        .map(span -> source.substring(span.startOffset(), span.endOffset()))
        .toList();
  }
}
