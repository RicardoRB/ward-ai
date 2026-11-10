package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RuleOptionType;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Reads the {@code category}, {@code presets} and {@code options} keys of a rule row. Every problem
 * is an {@link IllegalArgumentException} whose message the caller attaches to the rule file.
 */
public final class RuleRowMetadata {
  private static final String CATEGORY = "category";
  private static final String PRESETS = "presets";
  private static final String OPTIONS = "options";
  private static final Set<String> OPTION_KEYS =
      Set.of("type", "min", "max", "values", "parameter");

  private static final String MIN_JAVA_RELEASE = "min-java-release";

  private RuleRowMetadata() {}

  /** Returns the lowest Java release the row names, or 0 when it names none. */
  public static int minJavaRelease(final TomlTable row) {
    if (!row.contains(MIN_JAVA_RELEASE)) {
      return 0;
    }
    final Long release = row.getLong(MIN_JAVA_RELEASE);
    if (release == null || release < 1) {
      throw new IllegalArgumentException("'" + MIN_JAVA_RELEASE + "' must be a positive integer");
    }
    return Math.toIntExact(release);
  }

  /** Returns the category, or the empty text when the row has none. */
  public static String category(final TomlTable row) {
    if (!row.contains(CATEGORY)) {
      return "";
    }
    final String category = row.getString(CATEGORY);
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("'" + CATEGORY + "' must be a non-blank string");
    }
    return category;
  }

  /** Returns the presets the row lists, or none when it lists none. */
  public static Set<RulePreset> presets(final TomlTable row) {
    final Set<RulePreset> presets = EnumSet.noneOf(RulePreset.class);
    for (final String key : strings(row, PRESETS)) {
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

  /** Returns the options the row declares, in the order of its table. */
  public static List<RuleOption> options(final TomlTable row) {
    if (!row.contains(OPTIONS)) {
      return List.of();
    }
    final TomlTable table = row.getTable(OPTIONS);
    if (table == null) {
      throw new IllegalArgumentException("'" + OPTIONS + "' must be a table");
    }
    final List<RuleOption> options = new ArrayList<>();
    for (final String name : table.keySet()) {
      options.add(option(name, table.getTable(name)));
    }
    return options;
  }

  private static RuleOption option(final String name, final TomlTable table) {
    final String where = OPTIONS + "." + name;
    if (table == null) {
      throw new IllegalArgumentException("'" + where + "' must be a table");
    }
    for (final String key : table.keySet()) {
      if (!OPTION_KEYS.contains(key)) {
        throw new IllegalArgumentException("'" + where + "." + key + "' is not a declaration key");
      }
    }
    final String type = table.getString("type");
    final RuleOptionType resolved =
        RuleOptionType.byKey(type == null ? "" : type)
            .orElseThrow(
                () -> new IllegalArgumentException("'" + where + ".type' is not a known type"));
    return new RuleOption(
        name,
        resolved,
        table.getString("parameter"),
        table.contains("min") ? number(table, "min", where) : Long.MIN_VALUE,
        table.contains("max") ? number(table, "max", where) : Long.MAX_VALUE,
        strings(table, "values"));
  }

  private static long number(final TomlTable table, final String key, final String where) {
    final Long value = table.getLong(key);
    if (value == null) {
      throw new IllegalArgumentException("'" + where + "." + key + "' must be an integer");
    }
    return value;
  }

  private static List<String> strings(final TomlTable table, final String key) {
    if (!table.contains(key)) {
      return List.of();
    }
    final TomlArray array = table.getArray(key);
    if (array == null) {
      throw new IllegalArgumentException("'" + key + "' must be a list of strings");
    }
    final List<String> values = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      if (!array.isString(index)) {
        throw new IllegalArgumentException("'" + key + "' must be a list of strings");
      }
      values.add(array.getString(index));
    }
    return values;
  }
}
