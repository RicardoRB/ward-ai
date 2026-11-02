package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcessTreeRssTest {

  private static final long ROOT = 1;
  private static final long UNRELATED_PID = 300;
  private static final long PARENT = 200;
  private static final long PARENT_RSS = 300;
  private static final long CHILD_RSS = 50;
  private static final long OTHER_CHILD_RSS = 70;
  private static final long UNRELATED_RSS = 9000;
  private static final long UNKNOWN = 999;
  private static final String TABLE =
      """
      1      0    100
      200    1    300
      201    200  50
      202    200  70
      300    1    9000
      malformed row
      """;

  @Test
  void sumsRootAndAllDescendantsOnly() {
    assertThat(ProcessTreeRss.sum(PARENT, TABLE))
        .isEqualTo(PARENT_RSS + CHILD_RSS + OTHER_CHILD_RSS);
  }

  @Test
  void includesRootWithoutChildren() {
    assertThat(ProcessTreeRss.sum(UNRELATED_PID, TABLE)).isEqualTo(UNRELATED_RSS);
  }

  @Test
  void unknownRootCountsAsZero() {
    assertThat(ProcessTreeRss.sum(UNKNOWN, TABLE)).isZero();
  }

  @Test
  void ignoresRowsThatAreNotThreeNumbers() {
    assertThat(ProcessTreeRss.sum(ROOT, "  1 0 100 extra\nnot numbers here\n")).isZero();
  }
}
