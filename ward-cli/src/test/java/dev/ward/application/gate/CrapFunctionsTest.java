package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.domain.gate.FileLines;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class CrapFunctionsTest {

  private static final int TEN = 10;
  private static final int TWENTY = 20;
  private static final int THREE = 3;
  private static final int FIVE = 5;
  private static final int FOUR = 4;
  private static final double HALF = 0.5;
  private static final double TWO_THIRDS = 2.0 / 3;

  private static Measured function(final int first, final int last, final int complexity) {
    return new Measured(new Span(first, 1, last, 1, 0, 0), complexity);
  }

  private static Set<Integer> ints(final String csv) {
    return java.util.Arrays.stream(csv.split(","))
        .map(Integer::valueOf)
        .collect(java.util.stream.Collectors.toSet());
  }

  private static FileLines lines(final Set<Integer> coverable, final Set<Integer> covered) {
    return new FileLines(new TreeSet<>(coverable), new TreeSet<>(covered));
  }

  @Test
  void coverageIsTheCoveredShareOfTheCoverableLinesInsideTheDeclaration() {
    final FileLines file = lines(ints("11,12,13,14,15,16,17,18,30"), ints("11,12,13,14,30"));

    final List<CrapFunctions.Scored> scored =
        CrapFunctions.score(List.of(function(TEN, TWENTY, TEN)), Optional.of(file));

    assertThat(scored.get(0).score().coverage()).isEqualTo(OptionalDouble.of(HALF));
  }

  @Test
  void aLineCountsOnlyForTheInnermostFunction() {
    final FileLines file = lines(ints("2,5,6,9"), ints("5,6"));

    final List<CrapFunctions.Scored> scored =
        CrapFunctions.score(
            List.of(function(1, TWENTY, FOUR), function(FIVE, TEN, 2)), Optional.of(file));

    assertThat(scored.get(0).score().coverage()).isEqualTo(OptionalDouble.of(0));
    assertThat(scored.get(1).score().coverage()).isEqualTo(OptionalDouble.of(TWO_THIRDS));
  }

  @Test
  void aFunctionWithoutCoverableLinesOrAFileWithoutLinesHasNoCoverage() {
    final FileLines file = lines(ints("30"), ints("30"));

    assertThat(
            CrapFunctions.score(List.of(function(1, FIVE, THREE)), Optional.of(file))
                .get(0)
                .score()
                .coverage())
        .isEmpty();
    assertThat(
            CrapFunctions.score(List.of(function(1, FIVE, THREE)), Optional.empty())
                .get(0)
                .score()
                .score())
        .isEqualTo(THREE);
  }
}
