package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.types.UnitTypeFacts;
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
      run.facts(AnalysisLanguage.JAVA);
      run.facts(AnalysisLanguage.JAVA);
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
      run.facts(AnalysisLanguage.JAVA);
    }
    assertThat(started.closes).isOne();
    assertThat(idle.closes).isZero();
  }

  @Test
  void languageWithoutSidecarHasNoResolver() {
    try (TypeSidecarRun run = new TypeSidecarRun(Map.of())) {
      assertThat(run.facts(AnalysisLanguage.PYTHON)).isEmpty();
    }
  }

  @Test
  void handsTheClasspathFileToTheSidecar() {
    final java.nio.file.Path classpath = java.nio.file.Path.of("classpath.txt");
    final java.util.concurrent.atomic.AtomicReference<Optional<java.nio.file.Path>> seen =
        new java.util.concurrent.atomic.AtomicReference<>();
    final TypeSidecar sidecar =
        new TypeSidecar() {
          @Override
          public Optional<TypeFacts> start() {
            return Optional.empty();
          }

          @Override
          public Optional<TypeFacts> start(final Optional<java.nio.file.Path> classpathFile) {
            seen.set(classpathFile);
            return Optional.empty();
          }

          @Override
          public void close() {
            // Nothing runs.
          }
        };
    try (TypeSidecarRun run =
        new TypeSidecarRun(Map.of(AnalysisLanguage.JAVA, sidecar), Optional.of(classpath))) {
      run.facts(AnalysisLanguage.JAVA);
    }
    assertThat(seen.get()).contains(classpath);
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
    public Optional<TypeFacts> start() {
      starts++;
      if (!startable) {
        return Optional.empty();
      }
      return Optional.of((unit, spans, kinds) -> UnitTypeFacts.none());
    }

    @Override
    public void close() {
      closes++;
    }
  }
}
