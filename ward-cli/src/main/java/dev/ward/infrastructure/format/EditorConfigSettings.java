package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatDefaults;
import dev.ward.domain.format.FormatSettings;
import dev.ward.infrastructure.format.EditorConfigValues.Setting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Merges the matching sections of the applicable files, later sections and closer files winning,
 * and turns the merged values into {@link FormatSettings}. Unknown {@code ward_} keys are errors;
 * other keys that Ward does not use are ignored.
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
   * @param language the language of the file
   * @param declared the options the language's formatter declares
   * @param files the applicable files, farthest first
   * @return the settings
   */
  static FormatSettings resolve(
      final String relativePath,
      final AnalysisLanguage language,
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
    return new FormatSettings(
        EditorConfigValues.flag(effective, WARD_FORMAT, true),
        EditorConfigLayout.layout(effective, language),
        EditorConfigValues.numberOr(
            effective, WARD_MAX_BLANK_LINES, NO_BLANK_LINES, FormatDefaults.MAX_BLANK_LINES),
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
   * Tells whether Ward reads a key: a standard key, a shared {@code ward_} key or a declared one.
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

  private static Map<String, FormatOption> wardOptions(final List<FormatOption> declared) {
    final Map<String, FormatOption> keyed = new LinkedHashMap<>();
    for (final FormatOption option : declared) {
      keyed.put(wardKey(option), option);
    }
    return keyed;
  }

  private static FormatOptions options(
      final Map<String, Setting> effective, final List<FormatOption> declared) {
    final Map<String, String> values = new LinkedHashMap<>();
    for (final FormatOption option : declared) {
      final String key = wardKey(option);
      final Setting setting = effective.get(key);
      if (setting == null) {
        values.put(option.key(), option.defaultValue());
      } else if (option.accepts(setting.value())) {
        values.put(option.key(), setting.value());
      } else {
        throw setting.invalid(key, optionReason(option));
      }
    }
    return new FormatOptions(values);
  }

  /** Maps a plain kebab-case option key to its {@code ward_} EditorConfig key. */
  private static String wardKey(final FormatOption option) {
    return WARD_PREFIX + option.key().replace('-', '_');
  }

  private static String optionReason(final FormatOption option) {
    if (option.allowedValues().isEmpty()) {
      return "expected a " + option.type().name().toLowerCase(Locale.ROOT) + " value";
    }
    return "supported values are "
        + option.allowedValues().stream().collect(Collectors.joining(", "));
  }

  /**
   * Returns the path of a file relative to the folder of the {@code .editorconfig} that holds it.
   */
  private static String withinDirectory(final String relativePath, final String directory) {
    return directory.isEmpty() ? relativePath : relativePath.substring(directory.length() + 1);
  }
}
