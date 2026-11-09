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

class BiomeUnusedParameterCheckTest {

  @Test
  void reportsAnUnreadParameterAndSkipsAnUnderscoredOne() {
    assertThat(reported(true, "function f(a, _b) {\n  return 1;\n}\n")).containsExactly("a");
  }

  @Test
  void skipsADestructuredParameterWhenRestSiblingsAreIgnored() {
    final String source = "function f({ a, ...rest }) {\n  return rest;\n}\n";
    assertThat(reported(true, source)).isEmpty();
    assertThat(reported(false, source)).containsExactly("a");
  }

  @Test
  void keepsAParameterThatIsRead() {
    assertThat(reported(true, "const g = (x) => x + 1;\n")).isEmpty();
  }

  private static List<String> reported(final boolean ignoreRestSiblings, final String source) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final NodeScopeAnalysis analysis = new NodeScopeAnalysis(environments -> Set.of());
    final UnitScopes scopes = analysis.analyze(unit, root);
    return new BiomeUnusedParameterCheck(ignoreRestSiblings, "parameter '{value}' is never used")
        .matches(root, null, new ScopeView(scopes, Map.of(), Set.of(), analysis), unit).stream()
            .map(RuleMatch::span)
            .map(span -> source.substring(span.startOffset(), span.endOffset()))
            .toList();
  }
}
