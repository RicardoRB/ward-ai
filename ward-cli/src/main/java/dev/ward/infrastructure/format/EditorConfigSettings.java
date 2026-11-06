package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.domain.format.FormatSettings;
import dev.ward.infrastructure.format.EditorConfigValues.Setting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Merges the matching sections of the applicable files, later sections and closer files winning,
 * and turns the merged values into {@link FormatSettings}. Options come from the formatter's
 * declarations: each declared option takes its {@code .editorconfig} value, or its declared
 * default. Unknown {@code ward_} keys are errors; other keys that Ward does not use are ignored.
 */
final class EditorConfigSettings {

  static final String WARD_PREFIX = "ward_";
  static final String WARD_FORMAT = "ward_format";
  static final String WARD_MAX_BLANK_LINES = "ward_max_blank_lines";
  private static final String UNSET = "unset";
  private static final Set<String> SHARED_WARD_KEYS = Set.of(WARD_FORMAT, WARD_MAX_BLANK_LINES);
  private static final int NO_BLANK_LINES = 0;

  private EditorConfigSettings() {}

  /**
   * Resolves the settings of one file.
   *
   * @param relativePath the file path relative to the project root
   * @param declared the options the language's formatter declares, including the shared ones
   * @param files the applicable files, farthest first
   * @return the settings
   */
  static FormatSettings resolve(
      final String relativePath,
      final List<FormatOption> declared,
      final List<EditorConfigFile> files) {
    final Map<String, FormatOption> wardOptions = wardOptions(declared);
    final Map<String, Setting> effective = new LinkedHashMap<>();
    for (final EditorConfigFile file : files) {
      final String within = withinDirectory(relativePath, file.directory());
      for (final EditorConfigSection section : file.sections()) {
        if (section.pattern().matcher(within).matches()) {
          applySection(effective, wardOptions, file, section);
        }
      }
    }
    final FormatOptions defaults = FormatOptions.defaults(declared);
    return new FormatSettings(
        EditorConfigValues.flag(effective, WARD_FORMAT, true),
        EditorConfigLayout.layout(effective, defaults),
        EditorConfigValues.numberOr(
            effective,
            WARD_MAX_BLANK_LINES,
            NO_BLANK_LINES,
            EditorConfigLayout.declaredInt(
                defaults,
                EditorConfigLayout.MAX_BLANK_LINES_OPTION,
                EditorConfigLayout.FALLBACK_MAX_BLANK_LINES)),
        options(effective, declared));
  }

  private static void applySection(
      final Map<String, Setting> effective,
      final Map<String, FormatOption> wardOptions,
      final EditorConfigFile file,
      final EditorConfigSection section) {
    for (final Map.Entry<String, String> entry : section.properties().entrySet()) {
      apply(effective, wardOptions, file, section, entry);
    }
  }

  private static void apply(
      final Map<String, Setting> effective,
      final Map<String, FormatOption> wardOptions,
      final EditorConfigFile file,
      final EditorConfigSection section,
      final Map.Entry<String, String> entry) {
    final String key = entry.getKey();
    final String value = entry.getValue();
    if (!isKnown(key, wardOptions)) {
      rejectUnknownWardKey(file, section, key);
      return;
    }
    if (UNSET.equalsIgnoreCase(value)) {
      effective.remove(key);
    } else {
      final String stored = wardOptions.containsKey(key) ? value : value.toLowerCase(Locale.ROOT);
      effective.put(key, new Setting(stored, file.path(), section.header()));
    }
  }

  /**
   * Tells whether Ward reads a key: a standard key, a shared {@code ward_} key or a language key.
   */
  private static boolean isKnown(final String key, final Map<String, FormatOption> wardOptions) {
    return wardOptions.containsKey(key)
        || SHARED_WARD_KEYS.contains(key)
        || EditorConfigLayout.KEYS.contains(key);
  }

  /** An unknown {@code ward_} key is an error; an unknown key of another tool is ignored. */
  private static void rejectUnknownWardKey(
      final EditorConfigFile file, final EditorConfigSection section, final String key) {
    if (key.startsWith(WARD_PREFIX)) {
      throw EditorConfigValues.invalid(file.path(), section.header(), key, "unknown Ward option");
    }
  }

  /**
   * The language keys only; the shared options map to standard keys and {@code ward_} shared keys.
   */
  private static Map<String, FormatOption> wardOptions(final List<FormatOption> declared) {
    final Map<String, FormatOption> keyed = new LinkedHashMap<>();
    for (final FormatOption option : declared) {
      if (!EditorConfigOptions.isShared(option.key())) {
        keyed.put(WARD_PREFIX + option.key().replace('-', '_'), option);
      }
    }
    return keyed;
  }

  /**
   * Builds the option values for the declared options. A declared option that the configuration
   * does not set takes its declared default, and an undeclared shared option is not passed.
   */
  private static FormatOptions options(
      final Map<String, Setting> effective, final List<FormatOption> declared) {
    final Map<String, String> values = new LinkedHashMap<>();
    for (final FormatOption option : declared) {
      final String key = EditorConfigOptions.sourceKey(option.key(), effective);
      final Setting setting = effective.get(key);
      if (setting == null) {
        values.put(option.key(), option.defaultValue());
      } else {
        final String text = EditorConfigOptions.optionValue(option, setting.value());
        if (!option.accepts(text)) {
          throw setting.invalid(key, EditorConfigOptions.optionReason(option));
        }
        values.put(option.key(), text);
      }
    }
    return new FormatOptions(values);
  }

  /**
   * Returns the path of a file relative to the folder of the {@code .editorconfig} that holds it.
   */
  private static String withinDirectory(final String relativePath, final String directory) {
    return directory.isEmpty() ? relativePath : relativePath.substring(directory.length() + 1);
  }
}
