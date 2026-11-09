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

/** Runs the prefer-const check on parsed sources with the scopes of each unit. */
class NodePreferConstCheckTest {

  private static final int WRITE_LINE = 3;

  @Test
  void initializedVariableNeverAssignedIsReported() {
    assertThat(run("function f() { let s = 1; return s; }", false, false)).hasSize(1);
  }

  @Test
  void assignedVariableIsNotReported() {
    assertThat(run("function f() { let s = 1; s = 2; return s; }", false, false)).isEmpty();
  }

  @Test
  void singlePlainWriteIsReportedAtTheWrite() {
    final List<RuleMatch> found =
        run("function f() {\n  let c;\n  c = 1;\n  return c;\n}\n", false, false);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(WRITE_LINE);
  }

  @Test
  void writeInNestedScopeIsNotReported() {
    assertThat(run("function f(x) { let c; if (x) { c = 1; } return c; }", false, false)).isEmpty();
  }

  @Test
  void twoWritesAreNotReported() {
    assertThat(run("function f() { let c; c = 1; c = 2; return c; }", false, false)).isEmpty();
  }

  @Test
  void readBeforeWriteReportsTheDeclarationUnlessIgnored() {
    final String source = "function f() { let c; g(c); c = 1; }";

    assertThat(run(source, false, false)).hasSize(1);
    assertThat(run(source, false, true)).isEmpty();
  }

  @Test
  void destructuringAllKeepsADestructuringOnlyWhenEveryVariableQualifies() {
    final String source = "function f(s) { let { a, b } = s; a = 1; return a + b; }";

    assertThat(run(source, false, false)).hasSize(1);
    assertThat(run(source, true, false)).isEmpty();
  }

  private static List<RuleMatch> run(
      final String source, final boolean allDestructured, final boolean ignoreRead) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final NodeScopeAnalysis analysis = new NodeScopeAnalysis(environments -> Set.of());
    final UnitScopes scopes = analysis.analyze(unit, root);
    return new NodePreferConstCheck(allDestructured, ignoreRead, "{value} is never reassigned")
        .matches(root, null, new ScopeView(scopes, Map.of(), Set.of(), analysis), unit);
  }
}
