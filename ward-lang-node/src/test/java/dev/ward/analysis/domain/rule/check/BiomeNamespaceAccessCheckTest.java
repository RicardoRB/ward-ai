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

class BiomeNamespaceAccessCheckTest {

  private static final BiomeNamespaceAccessCheck CHECK = new BiomeNamespaceAccessCheck("message");

  @Test
  void reportsAComputedAccessToANamespaceImport() {
    assertThat(reported("import * as foo from 'foo';\nconst a = foo['bar'];\n"))
        .containsExactly("foo['bar']");
    assertThat(reported("import * as foo from 'foo';\nconst a = foo[key];\n"))
        .containsExactly("foo[key]");
  }

  @Test
  void ignoresADottedAccessAndANameThatIsNotANamespace() {
    assertThat(reported("import * as foo from 'foo';\nconst a = foo.bar;\n")).isEmpty();
    assertThat(reported("import { baz } from 'baz';\nconst a = baz['x'];\n")).isEmpty();
  }

  @Test
  void ignoresALocalThatShadowsTheNamespace() {
    assertThat(
            reported("import * as foo from 'foo';\nfunction f(foo) {\n  return foo['bar'];\n}\n"))
        .isEmpty();
  }

  private static List<String> reported(final String source) {
    final SourceUnit unit = new SourceUnit("m", AnalysisLanguage.TYPESCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    final NodeScopeAnalysis analysis = new NodeScopeAnalysis(environments -> Set.of());
    final UnitScopes scopes = analysis.analyze(unit, root);
    return CHECK
        .matches(root, null, new ScopeView(scopes, Map.of(), Set.of(), analysis), unit)
        .stream()
        .map(RuleMatch::span)
        .map(span -> source.substring(span.startOffset(), span.endOffset()))
        .toList();
  }
}
