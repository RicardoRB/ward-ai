package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.application.gate.AnalysisResultCodec;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonAnalysisResultCodecTest {
  private static final String KEY = "key-1";
  private static final AnalysisFinding FINDING =
      new AnalysisFinding(
          "ward:java/method-length",
          AnalysisSeverity.ERROR,
          "too long",
          "src/A.java",
          3,
          5,
          "https://example.invalid/rule");
  private final AnalysisResultCodec codec = new JsonAnalysisResultCodec(new ForyJsonCodec());

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

  @Test
  void otherFormatVersionIsAMiss() {
    final String value =
        codec.encode(KEY, List.of()).replace("\"formatVersion\":1", "\"formatVersion\":99");

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
}
