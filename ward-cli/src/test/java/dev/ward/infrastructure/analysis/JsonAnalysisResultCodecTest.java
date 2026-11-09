package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.application.gate.AnalysisResultCodec;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonAnalysisResultCodecTest {
  private static final int OTHER_VERSION = 99;
  private static final String KEY = "key-1";
  private static final AnalysisFinding FINDING =
      new AnalysisFinding(
          "ward:java/method-length",
          AnalysisSeverity.ERROR,
          "too long",
          "src/A.java",
          3,
          5,
          "https://example.invalid/rule",
          List.of());
  private final AnalysisResultCodec codec = new JsonAnalysisResultCodec(new ForyJsonCodec());

  /** Scenario: a finding with a path keeps its steps, with their spans, in order. */
  @Test
  void roundTripKeepsThePath() {
    final AnalysisFinding withPath =
        new AnalysisFinding(
            "ward:java/taint",
            AnalysisSeverity.ERROR,
            "untrusted",
            "src/A.java",
            9,
            5,
            null,
            List.of(
                new FlowStep(new Span(7, 18, 7, 26, 100, 108), "source: source"),
                new FlowStep(new Span(9, 5, 9, 16, 150, 161), "sink: sink argument 0")));

    assertThat(codec.decode(KEY, codec.encode(KEY, List.of(withPath)))).contains(List.of(withPath));
  }

  @Test
  void roundTripKeepsEveryFindingField() {
    final String value = codec.encode(KEY, List.of(FINDING));

    assertThat(codec.decode(KEY, value)).contains(List.of(FINDING));
  }

  @Test
  void cleanUnitRoundTripsAsEmptyList() {
    final String value = codec.encode(KEY, List.of());

    assertThat(codec.decode(KEY, value)).contains(List.of());
  }

  @Test
  void corruptValueIsAMiss() {
    final String value = codec.encode(KEY, List.of(FINDING));

    assertThat(codec.decode(KEY, value.substring(0, value.length() / 2))).isEmpty();
    assertThat(codec.decode(KEY, "not json")).isEmpty();
  }

  /** Scenario: an old-format cache entry is invalidated, never misread with a missing path. */
  @Test
  void previousFormatVersionIsAMiss() {
    final String value =
        codec
            .encode(KEY, List.of(FINDING))
            .replace(
                version(JsonAnalysisResultCodec.FORMAT_VERSION),
                version(JsonAnalysisResultCodec.FORMAT_VERSION - 1));

    assertThat(codec.decode(KEY, value)).isEmpty();
  }

  @Test
  void otherFormatVersionIsAMiss() {
    final String value =
        codec
            .encode(KEY, List.of())
            .replace(version(JsonAnalysisResultCodec.FORMAT_VERSION), version(OTHER_VERSION));

    assertThat(codec.decode(KEY, value)).isEmpty();
  }

  @Test
  void keyMismatchIsAMiss() {
    final String value = codec.encode(KEY, List.of(FINDING));

    assertThat(codec.decode("other-key", value)).isEmpty();
  }

  @Test
  void unknownSeverityIsAMiss() {
    final String value = codec.encode(KEY, List.of(FINDING)).replace("\"ERROR\"", "\"FATAL\"");

    assertThat(codec.decode(KEY, value)).isEmpty();
  }

  private static String version(final int version) {
    return "\"formatVersion\":" + version;
  }
}
