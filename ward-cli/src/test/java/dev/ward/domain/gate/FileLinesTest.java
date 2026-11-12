package dev.ward.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FileLinesTest {

  private static final int TEN = 10;
  private static final int ELEVEN = 11;
  private static final int TWENTY = 20;
  private static final int THREE = 3;
  private static final int SIX = 6;

  private static Set<Integer> ints(final String csv) {
    return csv.isEmpty()
        ? Set.of()
        : java.util.Arrays.stream(csv.split(","))
            .map(Integer::valueOf)
            .collect(java.util.stream.Collectors.toSet());
  }

  private static FileLines lines(final Set<Integer> coverable, final Set<Integer> covered) {
    return new FileLines(new TreeSet<>(coverable), new TreeSet<>(covered));
  }

  @Test
  void mergeMarksALineCoveredWhenEitherReportCoversIt() {
    final FileLines first = lines(ints("12,13"), ints(""));
    final FileLines second = lines(ints("12"), ints("12"));

    final FileLines merged = first.merge(second);

    assertThat(merged.coverable()).hasToString("[12, 13]");
    assertThat(merged.covered()).hasToString("[12]");
  }

  @Test
  void countsStayInsideTheRequestedRange() {
    final FileLines file = lines(ints("5,11,12,18,25"), ints("11,12,25"));

    assertThat(file.coverableIn(TEN, TWENTY)).isEqualTo(THREE);
    assertThat(file.coveredIn(TEN, TWENTY)).isEqualTo(2);
    assertThat(file.coverableIn(ELEVEN, ELEVEN)).isEqualTo(1);
  }

  @Test
  void mergeKeepsTheLargerCoveredBranchCountOfEachLine() {
    final FileLines first =
        new FileLines(
            new TreeSet<>(Set.of(1)),
            new TreeSet<>(Set.of(1)),
            new java.util.TreeMap<>(java.util.Map.of(1, 4, 2, 2)),
            new java.util.TreeMap<>(java.util.Map.of(1, 1, 2, 0)));
    final FileLines second =
        new FileLines(
            new TreeSet<>(Set.of(1)),
            new TreeSet<>(Set.of(1)),
            new java.util.TreeMap<>(java.util.Map.of(1, 4)),
            new java.util.TreeMap<>(java.util.Map.of(1, 3)));

    final FileLines merged = first.merge(second);

    assertThat(merged.branchCount()).isEqualTo(SIX);
    assertThat(merged.coveredBranchCount()).isEqualTo(THREE);
  }

  @Test
  void coveredLinesThatAreNotCoverableAreDropped() {
    assertThat(lines(ints("1"), ints("1,2")).covered()).hasToString("[1]");
  }
}
