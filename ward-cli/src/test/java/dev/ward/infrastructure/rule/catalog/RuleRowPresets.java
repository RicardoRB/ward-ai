package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.rule.RulePreset;
import java.util.EnumSet;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Reads the optional {@code presets} key of a catalog row. */
final class RuleRowPresets {
  private static final String PRESETS = "presets";

  private RuleRowPresets() {}

  /** Returns the presets the row lists, or none when it lists none. */
  static Set<RulePreset> read(final TomlTable row) {
    final Set<RulePreset> presets = EnumSet.noneOf(RulePreset.class);
    final TomlArray array = row.getArray(PRESETS);
    for (int index = 0; array != null && index < array.size(); index++) {
      final String key = array.getString(index);
      presets.add(
          RulePreset.byKey(key)
              .filter(preset -> preset != RulePreset.NONE)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "'" + PRESETS + "' value '" + key + "' is not standard or strict")));
    }
    return presets;
  }
}
