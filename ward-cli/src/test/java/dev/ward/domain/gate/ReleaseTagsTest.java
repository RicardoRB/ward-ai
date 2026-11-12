package dev.ward.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseTagsTest {
  private static final String FOUR = String.join(".", "1", "2", "0", "1");

  @Test
  void recognizesPlainVersionsOnly() {
    assertThat(List.of("1.2", "v1.2.3", String.join(".", "10", "0", "0", "1"), "v2.0"))
        .allMatch(ReleaseTags::isRelease);
    assertThat(List.of("v1", "v1.2.3-rc1", "release-1.2", "1.2.x", "latest"))
        .noneMatch(ReleaseTags::isRelease);
  }

  @Test
  void highestComparesSegmentsAsNumbers() {
    assertThat(ReleaseTags.highest(List.of("v1.2.0", "v1.10.0", "v1.9.3", "v1.10.0-rc1")))
        .contains("v1.10.0");
    assertThat(ReleaseTags.highest(List.of("1.2", FOUR))).contains(FOUR);
    assertThat(ReleaseTags.highest(List.of("nightly", "v1"))).isEmpty();
  }
}
