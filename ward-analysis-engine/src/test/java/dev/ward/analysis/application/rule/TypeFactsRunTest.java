package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypeFactsRunTest {
  private static final TypeFacts NATIVE = (unit, spans, kinds) -> UnitTypeFacts.none();

  @Test
  void aLanguageWithANativeSourceGetsTheTypesCapability() {
    final List<FrontendSupport> supports =
        new TypeFactsRun(Map.of(AnalysisLanguage.JAVA, NATIVE)).withTypes(frontends());

    assertThat(capabilities(supports, AnalysisLanguage.JAVA)).contains(RuleCapability.TYPES);
  }

  @Test
  void aLanguageWithoutANativeResolverKeepsItsFrontendUnchanged() {
    final List<FrontendSupport> supports =
        new TypeFactsRun(Map.of(AnalysisLanguage.JAVA, NATIVE)).withTypes(frontends());

    assertThat(capabilities(supports, AnalysisLanguage.PYTHON))
        .doesNotContain(RuleCapability.TYPES);
  }

  @Test
  void withoutAnySourceNoLanguageGetsTypes() {
    final List<FrontendSupport> supports = new TypeFactsRun(Map.of()).withTypes(frontends());

    assertThat(supports)
        .allSatisfy(
            support -> assertThat(support.capabilities()).doesNotContain(RuleCapability.TYPES));
  }

  @Test
  void factsReturnsTheRegisteredSourceOnly() {
    final TypeFactsRun run = new TypeFactsRun(Map.of(AnalysisLanguage.JAVA, NATIVE));

    assertThat(run.facts(AnalysisLanguage.JAVA)).contains(NATIVE);
    assertThat(run.facts(AnalysisLanguage.PYTHON)).isEmpty();
  }

  private static Set<RuleCapability> capabilities(
      final List<FrontendSupport> supports, final AnalysisLanguage language) {
    return supports.stream()
        .filter(support -> support.language() == language)
        .findFirst()
        .orElseThrow()
        .capabilities();
  }

  private static List<FrontendSupport> frontends() {
    return List.of(
        new FrontendSupport(
            AnalysisLanguage.JAVA,
            unit -> new ParseResult(root(), List.of()),
            Set.of(RuleCapability.SYNTAX)),
        new FrontendSupport(
            AnalysisLanguage.PYTHON,
            unit -> new ParseResult(root(), List.of()),
            Set.of(RuleCapability.SYNTAX)));
  }

  private static SyntaxNode root() {
    return new SyntaxNode("file", new Span(1, 1, 1, 2, 0, 1), Map.of(), List.of());
  }
}
