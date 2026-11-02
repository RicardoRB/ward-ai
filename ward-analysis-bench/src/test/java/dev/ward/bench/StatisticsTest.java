package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StatisticsTest {

  private static final int SAMPLE_COUNT = 20;
  private static final double FIRST = 1.0;
  private static final double MIDDLE = 3.0;
  private static final double LAST = 5.0;
  private static final double BASE = 100.0;
  private static final double LOW = 90.0;
  private static final double HIGH = 110.0;
  private static final double FOUR_SECOND = 2.0;
  private static final double FOURTH = 4.0;
  private static final double MEDIAN_OF_EVEN = 2.5;
  private static final double P50_RANK = 10.0;
  private static final double P95_RANK = 19.0;

  @Test
  void oddCountMedianIsTheMiddleSample() {
    final Statistics stats = Statistics.of(List.of(LAST, FIRST, MIDDLE));

    assertThat(stats.median()).isEqualTo(MIDDLE);
    assertThat(stats.min()).isEqualTo(FIRST);
    assertThat(stats.max()).isEqualTo(LAST);
  }

  @Test
  void evenCountMedianAveragesTheMiddleSamples() {
    assertThat(Statistics.of(List.of(FIRST, FOUR_SECOND, MIDDLE, FOURTH)).median())
        .isEqualTo(MEDIAN_OF_EVEN);
  }

  @Test
  void spreadIsMaxMinusMinOverMedian() {
    final Statistics stats = Statistics.of(List.of(LOW, BASE, HIGH));

    assertThat(stats.spread()).isEqualTo((HIGH - LOW) / BASE);
  }

  @Test
  void nearestRankPercentilesOfTwentySamples() {
    final List<Double> samples =
        IntStream.rangeClosed(1, SAMPLE_COUNT).mapToObj(i -> (double) i).toList();
    final Statistics stats = Statistics.of(samples);

    assertThat(stats.p50()).isEqualTo(P50_RANK);
    assertThat(stats.p95()).isEqualTo(P95_RANK);
  }

  @Test
  void zeroMedianHasZeroSpreadInsteadOfInfinity() {
    assertThat(Statistics.of(List.of(0.0, 0.0)).spread()).isZero();
  }

  @Test
  void rejectsEmptySamples() {
    assertThatThrownBy(() -> Statistics.of(List.of())).isInstanceOf(IllegalArgumentException.class);
  }
}
