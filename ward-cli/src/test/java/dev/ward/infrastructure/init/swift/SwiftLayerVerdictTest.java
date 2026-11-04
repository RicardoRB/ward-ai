package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the layer verdicts of the graph against the Harmonize rules' expectations for each
 * architecture style: the allowed layout passes, and the seeded violation fails its source layer.
 * {@link HarmonizeRulesSmokeTest} checks the same verdicts against real {@code swift test}.
 */
class SwiftLayerVerdictTest {

  @ParameterizedTest
  @ValueSource(strings = {"layered", "hexagonal", "clean"})
  void allowedLayoutPassesEveryLayerRule(final String style) throws IOException {
    assertThat(SwiftLayerVerdicts.failingLayers(style, SwiftArchitectureFiles.fixture(style)))
        .isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "layered, controller, repository",
    "hexagonal, domain, infrastructure",
    "clean, domain, infrastructure"
  })
  void seededViolationFailsOnlyItsSourceLayer(
      final String style, final String source, final String forbidden) throws IOException {
    final Map<String, String> files = new TreeMap<>(SwiftArchitectureFiles.fixture(style));
    files.putAll(SwiftArchitectureFiles.violation(source, forbidden));

    assertThat(SwiftLayerVerdicts.failingLayers(style, files)).containsExactly(source);
  }
}
