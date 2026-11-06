package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ExpressionTypes;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeFacts;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives {@link ProcessTypeSidecar} with small shell fakes that speak the protocol. */
class ProcessTypeSidecarTest {
  private static final Span AT = new Span(1, 1, 1, 4, 0, 3);
  private static final SourceUnit UNIT = new SourceUnit("A.java", AnalysisLanguage.JAVA, "int");
  private static final String PROBE_ANSWER = "{\\\"facts\\\":{}}";
  private static final String TYPED_ANSWER =
      "{\\\"facts\\\":{\\\"0:3\\\":{\\\"type\\\":{\\\"name\\\":\\\"int\\\",\\\"primitive\\\":true}}}}";

  @TempDir Path root;

  @Test
  void answersSpansWithTheTypesTheProcessReturns() {
    final Optional<TypeFacts> resolver =
        sidecar(
                "read probe; echo \""
                    + PROBE_ANSWER
                    + "\"; while read line; do echo \""
                    + TYPED_ANSWER
                    + "\"; done")
            .start();

    assertThat(resolver).isPresent();
    assertThat(resolver.get().typesOf(UNIT, List.of(AT)).types())
        .containsExactly(Map.entry(AT, "int"));
  }

  @Test
  void aProcessThatCannotStartIsUnavailable() {
    final ProcessTypeSidecar sidecar =
        new ProcessTypeSidecar("java", List.of("/no/such/ward-sidecar-binary"), root);

    assertThat(sidecar.start()).isEmpty();
  }

  @Test
  void aProcessThatExitsBeforeProbingIsUnavailable() {
    assertThat(sidecar("exit 0").start()).isEmpty();
  }

  @Test
  void aMalformedAnswerEndsTheSidecarForTheRun() {
    final TypeFacts resolver =
        sidecar(
                "read probe; echo \""
                    + PROBE_ANSWER
                    + "\"; while read line; do echo not-json; done")
            .start()
            .orElseThrow();

    assertThat(resolver.typesOf(UNIT, List.of(AT)).types()).isEmpty();
    assertThat(resolver.typesOf(UNIT, List.of(AT))).isEqualTo(ExpressionTypes.none());
  }

  @Test
  void aProcessThatExitsMidRunGivesNoLaterAnswers() {
    final TypeFacts resolver =
        sidecar("read probe; echo \"" + PROBE_ANSWER + "\"").start().orElseThrow();

    assertThat(resolver.typesOf(UNIT, List.of(AT)).types()).isEmpty();
    assertThat(resolver.typesOf(UNIT, List.of(AT)).types()).isEmpty();
  }

  @Test
  void closeStopsAProcessAndIsIdempotent() {
    final ProcessTypeSidecar sidecar =
        sidecar("read probe; echo \"" + PROBE_ANSWER + "\"; sleep 60");
    assertThat(sidecar.start()).isPresent();

    sidecar.close();
    sidecar.close();

    assertThat(sidecar.start()).isPresent();
    sidecar.close();
  }

  private ProcessTypeSidecar sidecar(final String script) {
    return new ProcessTypeSidecar("fake", List.of("sh", "-c", script), root);
  }
}
