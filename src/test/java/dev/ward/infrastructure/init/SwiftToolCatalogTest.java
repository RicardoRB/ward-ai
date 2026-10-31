package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pins the SwiftLint release init writes into the Ward tools package. */
class SwiftToolCatalogTest {

  @Test
  void pinsTheNewestSwiftLintPluginsReleaseOlderThanFourteenDays() {
    assertThat(SwiftToolCatalog.SWIFTLINT_PLUGINS_VERSION).isEqualTo("0.65.1");
    assertThat(SwiftToolCatalog.SWIFTLINT_PLUGINS_URL)
        .isEqualTo("https://github.com/SimplyDanny/SwiftLintPlugins");
  }
}
