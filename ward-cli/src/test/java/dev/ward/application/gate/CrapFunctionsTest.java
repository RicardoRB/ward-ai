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

  private static Measured function(final int first, final int last, final int complexity) {
    return new Measured(new Span(first, 1, last, 1, 0, 0), complexity);
  }

  private static FileLines lines(final Set<Integer> coverable, final Set<Integer> covered) {
    return new FileLines(new TreeSet<>(coverable), new TreeSet<>(covered));
  }

  @Test
  void coverageIsTheCoveredShareOfTheCoverableLinesInsideTheDeclaration() {
    final FileLines file =
        lines(Set.of(11, 12, 13, 14, 15, 16, 17, 18, 30), Set.of(11, 12, 13, 14, 30));

    final List<CrapFunctions.Scored> scored =
        CrapFunctions.score(List.of(function(10, 20, 10)), Optional.of(file));

    assertThat(scored.get(0).score().coverage()).isEqualTo(OptionalDouble.of(0.5));
  }

  @Test
  void aLineCountsOnlyForTheInnermostFunction() {
    final FileLines file = lines(Set.of(2, 5, 6, 9), Set.of(5, 6));

    final List<CrapFunctions.Scored> scored =
        CrapFunctions.score(List.of(function(1, 20, 4), function(5, 10, 2)), Optional.of(file));

    assertThat(scored.get(0).score().coverage()).isEqualTo(OptionalDouble.of(0));
    assertThat(scored.get(1).score().coverage()).isEqualTo(OptionalDouble.of(2.0 / 3));
  }

  @Test
  void aFunctionWithoutCoverableLinesOrAFileWithoutLinesHasNoCoverage() {
    final FileLines file = lines(Set.of(30), Set.of(30));

    assertThat(
            CrapFunctions.score(List.of(function(1, 5, 3)), Optional.of(file))
                .get(0)
                .score()
                .coverage())
        .isEmpty();
    assertThat(
            CrapFunctions.score(List.of(function(1, 5, 3)), Optional.empty())
                .get(0)
                .score()
                .score())
        .isEqualTo(3);
  }
}
