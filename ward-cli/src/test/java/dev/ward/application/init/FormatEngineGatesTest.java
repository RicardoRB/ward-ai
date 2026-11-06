package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.FormatEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The gate ids the {@code ward} format engine keeps, replaces, or adds. */
class FormatEngineGatesTest {

  @Test
  void theFirstExternalFormatterBecomesWardFormatAndTheOthersAreDropped() {
    assertThat(
            FormatEngineGates.apply(
                List.of("compile", "format", "checkstyle", "prettier"), FormatEngine.WARD))
        .containsExactly("compile", "ward-format", "checkstyle");
  }

  @Test
  void biomeKeepsItsPlaceAndWardFormatFollowsItWhenNoExternalFormatterIsSelected() {
    assertThat(FormatEngineGates.apply(List.of("biome", "vitest"), FormatEngine.WARD))
        .containsExactly("biome", "vitest", "ward-format");
  }

  @Test
  void theToolEngineAndSelectionsWithoutFormattingKeepTheIdsUnchanged() {
    final List<String> ids = List.of("format", "checkstyle");

    assertThat(FormatEngineGates.apply(ids, FormatEngine.TOOL)).isEqualTo(ids);
    assertThat(FormatEngineGates.apply(List.of("pmd"), FormatEngine.WARD)).containsExactly("pmd");
  }
}
