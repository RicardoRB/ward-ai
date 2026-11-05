package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.ExpressionTypes;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeResolver;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypeSidecarRunTest {
  private static final String TYPED = "ward:java/typed-rule";
  private static final FrontendSupport JAVA_SYNTAX =
      new FrontendSupport(AnalysisLanguage.JAVA, unit -> null, Set.of(RuleCapability.SYNTAX));

  @Test
  void noSidecarStartsWhenNoRuleNeedsTypes() {
    final CountingSidecar sidecar = new CountingSidecar(true);
    try (TypeSidecarRun run = new TypeSidecarRun(Map.of(AnalysisLanguage.JAVA, sidecar))) {
      final List<FrontendSupport> frontends =
          run.withTypes(List.of(JAVA_SYNTAX), List.of(syntaxRule()));

      assertThat(frontends).containsExactly(JAVA_SYNTAX);
    }
    assertThat(sidecar.starts).isZero();
  }

  @Test
  void sidecarThatStartsAddsTypesToTheFrontendOfARuleThatNeedsThem() {
    final CountingSidecar sidecar = new CountingSidecar(true);
    try (TypeSidecarRun run = new TypeSidecarRun(Map.of(AnalysisLanguage.JAVA, sidecar))) {
      final List<FrontendSupport> frontends =
          run.withTypes(List.of(JAVA_SYNTAX), List.of(typedRule()));

      assertThat(frontends.getFirst().capabilities())
          .containsExactlyInAnyOrder(RuleCapability.SYNTAX, RuleCapability.TYPES);
    }
  }

  /**
   * Adding TYPES must copy every other component of the frontend. Guards against a new component,
   * such as a control-flow lowering, being dropped when the frontend is rebuilt.
   */
  @Test
  void addingTypesKeepsEveryOtherFrontendComponent() throws ReflectiveOperationException {
    final CountingSidecar sidecar = new CountingSidecar(true);
    try (TypeSidecarRun run = new TypeSidecarRun(Map.of(AnalysisLanguage.JAVA, sidecar))) {
      final FrontendSupport typed =
          run.withTypes(List.of(JAVA_SYNTAX), List.of(typedRule())).getFirst();

      for (final RecordComponent component : FrontendSupport.class.getRecordComponents()) {
        if (!"capabilities".equals(component.getName())) {
          final Method accessor = component.getAccessor();
          assertThat(accessor.invoke(typed)).isEqualTo(accessor.invoke(JAVA_SYNTAX));
        }
      }
    }
  }

  @Test
  void aSidecarThatCannotStartLeavesTheFrontendWithoutTypes() {
    try (TypeSidecarRun run =
        new TypeSidecarRun(Map.of(AnalysisLanguage.JAVA, new CountingSidecar(false)))) {
      final List<FrontendSupport> frontends =
          run.withTypes(List.of(JAVA_SYNTAX), List.of(typedRule()));

      assertThat(frontends).containsExactly(JAVA_SYNTAX);
    }
  }

  @Test
  void aFailedStartIsNotRetriedInTheRun() {
    final CountingSidecar sidecar = new CountingSidecar(false);
    try (TypeSidecarRun run = new TypeSidecarRun(Map.of(AnalysisLanguage.JAVA, sidecar))) {
      run.resolver(AnalysisLanguage.JAVA);
      run.resolver(AnalysisLanguage.JAVA);
    }
    assertThat(sidecar.starts).isOne();
  }

  @Test
  void closeStopsEverySidecarThatStarted() {
    final CountingSidecar started = new CountingSidecar(true);
    final CountingSidecar idle = new CountingSidecar(true);
    try (TypeSidecarRun run =
        new TypeSidecarRun(
            Map.of(AnalysisLanguage.JAVA, started, AnalysisLanguage.TYPESCRIPT, idle))) {
      run.resolver(AnalysisLanguage.JAVA);
    }
    assertThat(started.closes).isOne();
    assertThat(idle.closes).isZero();
  }

  @Test
  void languageWithoutSidecarHasNoResolver() {
    try (TypeSidecarRun run = new TypeSidecarRun(Map.of())) {
      assertThat(run.resolver(AnalysisLanguage.PYTHON)).isEmpty();
    }
  }

  private static RuleDefinition typedRule() {
    return new RuleDefinition(
        TYPED, Set.of(AnalysisLanguage.JAVA), RuleCapability.TYPES, AnalysisSeverity.ERROR, "body");
  }

  private static RuleDefinition syntaxRule() {
    return new RuleDefinition(
        "ward:java/syntax-rule",
        Set.of(AnalysisLanguage.JAVA),
        RuleCapability.SYNTAX,
        AnalysisSeverity.ERROR,
        "body");
  }

  /** A sidecar that counts its starts and closes, and starts or fails as configured. */
  private static final class CountingSidecar implements TypeSidecar {
    private final boolean startable;
    int starts;
    int closes;

    CountingSidecar(final boolean startable) {
      this.startable = startable;
    }

    @Override
    public Optional<TypeResolver> start() {
      starts++;
      if (!startable) {
        return Optional.empty();
      }
      return Optional.of((SourceUnit unit, List<Span> spans) -> ExpressionTypes.none());
    }

    @Override
    public void close() {
      closes++;
    }
  }
}
