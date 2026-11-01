package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.init.swift.SwiftToolCatalog.SwiftToolRow;
import org.junit.jupiter.api.Test;

/** Pins the Swift tool releases init writes and the Xcode ranges they are proven on. */
class SwiftToolCatalogTest {

  @Test
  void aRowCoversItsXcodeRangeUpToButNotIncludingTheMaximum() {
    assertThat(SwiftToolCatalog.forXcode(SwiftToolCatalog.SWIFTLINT, "26.0")).isPresent();
    assertThat(SwiftToolCatalog.forXcode(SwiftToolCatalog.SWIFTLINT, "27.2")).isPresent();
    assertThat(SwiftToolCatalog.forXcode(SwiftToolCatalog.SWIFTLINT, "28.0")).isEmpty();
    assertThat(SwiftToolCatalog.forXcode(SwiftToolCatalog.HARMONIZE, "25.9")).isEmpty();
  }

  @Test
  void anUnreadableXcodeVersionIsCoveredByNoRow() {
    assertThat(SwiftToolCatalog.forXcode(SwiftToolCatalog.SWIFTLINT, "beta")).isEmpty();
  }

  @Test
  void rowsOfOneToolHaveDisjointRanges() {
    final SwiftToolRow early = row("26.0", "26.6");
    final SwiftToolRow late = row("26.6", "28.0");
    final SwiftToolRow overlapping = row("26.3", "27.0");

    assertThat(early.overlaps(late)).isFalse();
    assertThat(early.overlaps(overlapping)).isTrue();
  }

  @Test
  void eachToolHasOneRow() {
    for (final String tool : SwiftToolCatalog.tools()) {
      final long rows =
          SwiftToolCatalog.rows().stream().filter(row -> row.tool().equals(tool)).count();
      assertThat(rows).isEqualTo(1);
    }
  }

  @Test
  void compareXcodeOrdersByMajorThenMinor() {
    assertThat(SwiftToolCatalog.compareXcode("26.10", "26.6")).isPositive();
    assertThat(SwiftToolCatalog.compareXcode("27.0", "26.9")).isPositive();
    assertThat(SwiftToolCatalog.compareXcode("26.0", "26.0.1")).isZero();
  }

  private static SwiftToolRow row(final String minimum, final String maximum) {
    return new SwiftToolRow("tool", "Tool", "1.0.0", minimum, maximum);
  }

  @Test
  void pinsTheNewestSwiftLintPluginsReleaseOlderThanFourteenDays() {
    assertThat(SwiftToolCatalog.SWIFTLINT_PLUGINS_VERSION).isEqualTo("0.65.1");
    assertThat(SwiftToolCatalog.SWIFTLINT_PLUGINS_URL)
        .isEqualTo("https://github.com/SimplyDanny/SwiftLintPlugins");
  }

  @Test
  void pinsHarmonize130ExactlyFromItsRepository() {
    assertThat(SwiftToolCatalog.HARMONIZE_VERSION).isEqualTo("1.3.0");
    assertThat(SwiftToolCatalog.HARMONIZE_URL)
        .isEqualTo("https://github.com/perrystreetsoftware/Harmonize.git");
  }
}
