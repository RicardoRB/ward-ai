package dev.ward.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FileLinesTest {

  private static FileLines lines(final Set<Integer> coverable, final Set<Integer> covered) {
    return new FileLines(new TreeSet<>(coverable), new TreeSet<>(covered));
  }

  @Test
  void mergeMarksALineCoveredWhenEitherReportCoversIt() {
    final FileLines first = lines(Set.of(12, 13), Set.of());
    final FileLines second = lines(Set.of(12), Set.of(12));

    final FileLines merged = first.merge(second);

    assertThat(merged.coverable()).containsExactly(12, 13);
    assertThat(merged.covered()).containsExactly(12);
  }

  @Test
  void countsStayInsideTheRequestedRange() {
    final FileLines file = lines(Set.of(5, 11, 12, 18, 25), Set.of(11, 12, 25));

    assertThat(file.coverableIn(10, 20)).isEqualTo(3);
    assertThat(file.coveredIn(10, 20)).isEqualTo(2);
    assertThat(file.coverableIn(11, 11)).isEqualTo(1);
  }

  @Test
  void coveredLinesThatAreNotCoverableAreDropped() {
    assertThat(lines(Set.of(1), Set.of(1, 2)).covered()).containsExactly(1);
  }
}
