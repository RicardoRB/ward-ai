package dev.ward.application.init;

import dev.ward.domain.init.FormatEngine;
import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.InitGateOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Swaps the external format gates of a selection for {@code ward-format} under the ward engine. */
final class FormatEngineGates {
  private FormatEngineGates() {}

  /**
   * Returns {@code ids} with the {@code ward} format engine: the first replaced format gate becomes
   * {@code ward-format} in its place and the others are dropped. Biome keeps its place, and {@code
   * ward-format} follows it when no external formatter was selected. Under the tool engine, or when
   * no gate formats, {@code ids} is returned unchanged.
   */
  static List<String> apply(final List<String> ids, final FormatEngine engine) {
    if (engine != FormatEngine.WARD || !FormatGates.formats(ids)) {
      return ids;
    }
    final List<String> result = new ArrayList<>();
    boolean placed = false;
    for (final String id : ids) {
      if (FormatGates.REPLACED.contains(id)) {
        if (!placed) {
          result.add(FormatGates.WARD_FORMAT);
          placed = true;
        }
      } else {
        result.add(id);
      }
    }
    if (!placed) {
      result.add(FormatGates.WARD_FORMAT);
    }
    return result;
  }

  /** The {@code ward-format} gate: it has no command, because Ward runs it in-process. */
  static InitGateOption wardFormat() {
    return new InitGateOption(
        FormatGates.WARD_FORMAT,
        "Checks formatting with Ward's native formatter, configured by .editorconfig.",
        List.of(),
        true,
        null,
        Map.of());
  }
}
