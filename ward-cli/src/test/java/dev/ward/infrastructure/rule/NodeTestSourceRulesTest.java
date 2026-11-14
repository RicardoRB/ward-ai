package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.node.NodeControlFlow;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The hard-coded-secret rule leaves out test sources and still reports production code. */
class NodeTestSourceRulesTest {

  private static final String SECRET = "export const options = { secret: 'abcdef' };\n";

  @ParameterizedTest
  @ValueSource(strings = {"src/a.spec.ts", "src/a.test.ts", "src/__tests__/a.ts", "test/a.ts"})
  void testSourcesAreNotReported(final String path) {
    assertThat(count(path)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"src/a.ts", "src/spec.ts", "src/latest.ts"})
  void productionSourcesAreReported(final String path) {
    assertThat(count(path)).isEqualTo(1);
  }

  private static int count(final String path) {
    final RuleDefinition rule =
        new ClasspathRuleSource()
            .load().rules().stream()
                .filter(candidate -> "ward:typescript/hard-coded-secret".equals(candidate.id()))
                .findFirst()
                .orElseThrow();
    final FrontendSupport support =
        new FrontendSupport(
            AnalysisLanguage.TYPESCRIPT,
            new AntlrLanguageFrontend(),
            DataFlowFixtures.FLOW,
            new NodeControlFlow());
    return new RuleEngine(BundledRuleMatchers.create())
        .run(
            List.of(new SourceUnit(path, AnalysisLanguage.TYPESCRIPT, SECRET)),
            List.of(support),
            List.of(rule))
        .findings()
        .size();
  }
}
