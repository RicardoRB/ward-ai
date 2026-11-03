package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.application.gate.AnalysisResultCodec;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonDependencyCodecTest {
  private static final Span AT = new Span(2, 3, 2, 9, 12, 18);
  private static final FileSummary SUMMARY =
      new FileSummary(
          "src/A.kt",
          List.of(new Symbol("N", AT)),
          List.of(new Reference("B", AT)),
          List.of(new Symbol("Q", AT)));

  private final AnalysisResultCodec codec = new JsonAnalysisResultCodec(new ForyJsonCodec());

  @Test
  void summaryReadsBackWithItsPositions() {
    final String value = codec.encodeSummary("key", SUMMARY);

    assertThat(codec.decodeSummary("key", value)).contains(SUMMARY);
  }

  @Test
  void summaryStoredUnderAnotherKeyIsAMiss() {
    final String value = codec.encodeSummary("key", SUMMARY);

    assertThat(codec.decodeSummary("other", value)).isEmpty();
  }

  @Test
  void corruptSummaryIsAMiss() {
    assertThat(codec.decodeSummary("key", "{not json")).isEmpty();
    assertThat(codec.decodeSummary("key", "{\"formatVersion\":1,\"key\":\"key\"}")).isEmpty();
  }

  @Test
  void indexListsTheDependentsOfEachTarget() {
    final DependencyGraph graph =
        new DependencyGraph(
            List.of(
                new DependencyEdge("B.kt", "A.kt", new Reference("A", AT)),
                new DependencyEdge("C.kt", "A.kt", new Reference("A", AT))));

    final String index = codec.encodeIndex(graph);

    assertThat(index).contains("\"path\":\"A.kt\"").contains("\"dependents\":[\"B.kt\",\"C.kt\"]");
  }
}
