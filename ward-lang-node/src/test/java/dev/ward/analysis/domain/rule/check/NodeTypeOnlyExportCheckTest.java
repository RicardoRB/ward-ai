package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeScopeAnalysis;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the type-only export check on parsed TypeScript with the scopes of each unit. */
class NodeTypeOnlyExportCheckTest {

  @Test
  void clauseOfOnlyInterfacesIsReportedWithTheWholeMessage() {
    final List<RuleMatch> found =
        run("interface A { x: number }\ninterface B { y: number }\nexport { A, B };\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("all");
  }

  @Test
  void mixedClauseIsReportedOnceNamingItsTypeOnlyExports() {
    final List<RuleMatch> found =
        run("interface A { x: number }\ntype B = number;\nconst c = 1;\nexport { A, B, c };\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("exports \"A\" and \"B\" are");
  }

  @Test
  void singleTypeInAMixedClauseUsesTheSingularWording() {
    final List<RuleMatch> found =
        run("interface A { x: number }\nconst c = 1;\nexport { A, c };\n");

    assertThat(found.get(0).message()).isEqualTo("export \"A\" is");
  }

  @Test
  void typeOnlyImportExportedAsItsOwnNameIsReported() {
    assertThat(run("import type { T } from './t';\nexport { T };\n")).hasSize(1);
  }

  @Test
  void exportTypeClauseIsNotReported() {
    assertThat(run("interface A { x: number }\nexport type { A };\n")).isEmpty();
  }

  @Test
  void inlineTypeSpecifierIsNotReported() {
    assertThat(run("interface A { x: number }\nconst c = 1;\nexport { type A, c };\n")).isEmpty();
  }

  @Test
  void valueClauseIsNotReported() {
    assertThat(run("const c = 1;\nexport { c };\n")).isEmpty();
  }

  @Test
  void reexportFromAnotherModuleIsNotReported() {
    assertThat(run("export { Shape } from './shape';\n")).isEmpty();
  }

  private static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final NodeScopeAnalysis analysis = new NodeScopeAnalysis(environments -> Set.of());
    final UnitScopes scopes = analysis.analyze(unit, root);
    return new NodeTypeOnlyExportCheck("all", "{noun} {names} {verb}")
        .matches(root, null, new ScopeView(scopes, Map.of(), Set.of(), analysis), unit);
  }
}
