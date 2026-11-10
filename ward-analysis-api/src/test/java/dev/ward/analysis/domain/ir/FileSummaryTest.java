package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileSummaryTest {
  private static final Span SPAN = new Span(1, 1, 1, 5, 0, 4);

  @Test
  void copiesListsSoLaterChangesDoNotReachTheSummary() {
    final List<Symbol> exports = new ArrayList<>(List.of(new Symbol("N", SPAN)));
    final FileSummary summary = new FileSummary("A.java", exports, List.of(), List.of());

    exports.clear();

    assertThat(summary.exports()).containsExactly(new Symbol("N", SPAN));
    assertThatThrownBy(() -> summary.exports().add(new Symbol("M", SPAN)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsMissingFields() {
    assertThatThrownBy(() -> new FileSummary(null, List.of(), List.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new FileSummary("A.java", null, List.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new FileSummary("A.java", List.of(), null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new FileSummary("A.java", List.of(), List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsMissingElements() {
    assertThatThrownBy(
            () -> new FileSummary("A.java", Arrays.asList((Symbol) null), List.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void referenceAndEdgeRejectMissingFields() {
    assertThatThrownBy(() -> new Reference(null, SPAN)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Reference("A", null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Symbol(null, SPAN)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DependencyEdge("B.java", null, new Reference("A", SPAN)))
        .isInstanceOf(NullPointerException.class);
  }
}
