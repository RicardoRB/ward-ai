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

/** Runs the type-only import check on parsed TypeScript with the scopes of each unit. */
class NodeTypeOnlyImportCheckTest {

  @Test
  void importUsedOnlyInATypeAnnotationIsReported() {
    assertThat(run("import { Shape } from './shape';\nexport function f(s: Shape) { return s; }\n"))
        .hasSize(1);
  }

  @Test
  void importUsedAsATypeArgumentIsReported() {
    assertThat(
            run(
                "import { Shape } from './shape';\nexport function g(): Array<Shape> { return []; }\n"))
        .hasSize(1);
  }

  @Test
  void importUsedInAnImplementsClauseIsReported() {
    assertThat(
            run("import { Shape } from './shape';\nexport class C implements Shape { x = 1; }\n"))
        .hasSize(1);
  }

  @Test
  void importUsedAsAValueIsNotReported() {
    assertThat(run("import { helper } from './h';\nhelper();\n")).isEmpty();
  }

  @Test
  void importTypeDeclarationIsNotReported() {
    assertThat(run("import type { Shape } from './s';\nlet x: Shape;\n")).isEmpty();
  }

  @Test
  void unusedImportIsNotReported() {
    assertThat(run("import { Shape } from './shape';\n")).isEmpty();
  }

  private static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final NodeScopeAnalysis analysis = new NodeScopeAnalysis(environments -> Set.of());
    final UnitScopes scopes = analysis.analyze(unit, root);
    return new NodeTypeOnlyImportCheck("type")
        .matches(root, null, new ScopeView(scopes, Map.of(), Set.of(), analysis), unit);
  }
}
