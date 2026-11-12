package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.SecretAllowlist;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Reads the {@code allowlists} of a secret rule body: each table has {@code paths}, {@code
 * regexes}, {@code stopwords}, a {@code regex-target} of {@code secret}, {@code match} or {@code
 * line}, and a {@code condition} of {@code or} or {@code and}.
 */
final class SecretAllowlistParser {

  private static final Set<String> KEYS =
      Set.of("paths", "regexes", "stopwords", "regex-target", "condition");

  private SecretAllowlistParser() {}

  /** Returns the allowlists of {@code array}, or none when it is {@code null}. */
  static List<SecretAllowlist> parse(final TomlArray array) {
    final List<SecretAllowlist> lists = new ArrayList<>();
    for (int index = 0; array != null && index < array.size(); index++) {
      lists.add(allowlist(array.getTable(index)));
    }
    return lists;
  }

  /** Compiles {@code regex} with the flags of the secret rules. */
  static Pattern compile(final String regex, final String key) {
    try {
      return Pattern.compile(regex, Pattern.UNIX_LINES);
    } catch (final PatternSyntaxException exception) {
      throw new RuleConfigurationException(
          "'" + key + "' is not a valid regular expression", exception);
    }
  }

  /** Rejects the first key of {@code keys} that is not in {@code known}. */
  static void unknownKeys(final Set<String> keys, final Set<String> known, final String prefix) {
    for (final String key : keys) {
      if (!known.contains(key)) {
        throw new RuleConfigurationException("'" + prefix + key + "' is not a known key");
      }
    }
  }

  private static SecretAllowlist allowlist(final TomlTable table) {
    unknownKeys(table.keySet(), KEYS, "allowlists.");
    final String target = text(table, "regex-target", "secret");
    final String condition = text(table, "condition", "or");
    return new SecretAllowlist(
        patterns(table, "paths"),
        patterns(table, "regexes"),
        strings(table, "stopwords").stream().map(word -> word.toLowerCase(Locale.ROOT)).toList(),
        SecretAllowlist.Target.valueOf(
            oneOf(target, "regex-target", "secret", "match", "line").toUpperCase(Locale.ROOT)),
        "and".equals(oneOf(condition, "condition", "or", "and")));
  }

  private static String text(final TomlTable table, final String key, final String fallback) {
    final String value = table.getString(key);
    return value == null ? fallback : value;
  }

  private static String oneOf(final String value, final String key, final String... allowed) {
    if (!List.of(allowed).contains(value)) {
      throw new RuleConfigurationException(
          "'" + key + "' must be one of " + String.join(", ", allowed));
    }
    return value;
  }

  private static List<Pattern> patterns(final TomlTable table, final String key) {
    return strings(table, key).stream().map(regex -> compile(regex, key)).toList();
  }

  private static List<String> strings(final TomlTable table, final String key) {
    final TomlArray array = table.getArray(key);
    final List<String> values = new ArrayList<>();
    for (int index = 0; array != null && index < array.size(); index++) {
      values.add(TomlValues.stringAt(array, index, key));
    }
    return values;
  }
}
