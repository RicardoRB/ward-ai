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

/** Runs the hidden-field check on parsed sources with the scopes of each unit. */
class NodeHiddenFieldCheckTest {

  @Test
  void parameterNamedAsAFieldIsReported() {
    assertThat(run("class A { count = 0; bump(count) { return count; } }", false, false))
        .hasSize(1);
  }

  @Test
  void localNamedAsAFieldIsReported() {
    assertThat(
            run("class A { count = 0; bump() { const count = 1; return count; } }", false, false))
        .hasSize(1);
  }

  @Test
  void nameWithoutAFieldIsNotReported() {
    assertThat(run("class A { bump(count) { return count; } }", false, false)).isEmpty();
  }

  @Test
  void constructorParameterIsReportedUnlessIgnored() {
    final String source = "class A { x = 0; constructor(x) { this.x = x; } }";

    assertThat(run(source, false, false)).hasSize(1);
    assertThat(run(source, true, false)).isEmpty();
  }

  @Test
  void setterParameterIsReportedUnlessIgnored() {
    final String source = "class A { name = ''; setName(name) { this.name = name; } }";

    assertThat(run(source, false, false)).hasSize(1);
    assertThat(run(source, false, true)).isEmpty();
  }

  @Test
  void constructorParameterPropertyIsAFieldForOtherMethods() {
    assertThat(
            run(
                "class A { constructor(private x: number) {} m(x: number) { return x; } }",
                false,
                false))
        .hasSize(1);
  }

  private static List<RuleMatch> run(
      final String source, final boolean ignoreConstructor, final boolean ignoreSetter) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final NodeScopeAnalysis analysis = new NodeScopeAnalysis(environments -> Set.of());
    final UnitScopes scopes = analysis.analyze(unit, root);
    return new NodeHiddenFieldCheck(ignoreConstructor, ignoreSetter, "'{value}' hides a field")
        .matches(root, null, new ScopeView(scopes, Map.of(), Set.of(), analysis), unit);
  }
}
