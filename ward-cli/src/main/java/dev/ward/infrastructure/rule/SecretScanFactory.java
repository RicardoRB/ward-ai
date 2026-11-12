package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.SecretAllowlist;
import dev.ward.analysis.domain.rule.check.SecretRule;
import dev.ward.analysis.domain.rule.check.SecretScanCheck;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the {@code secret-scan} check of a rule body. The body keys are {@code regex}, {@code
 * path}, {@code keywords}, {@code entropy}, {@code secret-group}, {@code span}, {@code allowlists}
 * and {@code allowlist}, beside {@code check}, {@code description} and {@code message}.
 */
final class SecretScanFactory {

  private static final Set<String> KEYS =
      Set.of(
          "check",
          "description",
          "message",
          "regex",
          "path",
          "keywords",
          "entropy",
          "secret-group",
          "span",
          "allowlists",
          "allowlist");
  private static final String DEFAULT_ALLOWLIST = "default";
  private static final String SPAN = "span";

  private SecretScanFactory() {}

  /**
   * Builds the check of {@code toml}, with the global allowlist it names.
   *
   * @throws RuleConfigurationException if a key is unknown, or a value is unusable
   */
  static RuleCheck create(final TomlParseResult toml) {
    SecretAllowlistParser.unknownKeys(toml.keySet(), KEYS, "");
    final Optional<Pattern> regex = pattern(toml, "regex");
    final int group = TomlValues.optionalInteger(toml, "secret-group", 0);
    if (regex.isPresent() && group > regex.get().matcher("").groupCount()) {
      throw new RuleConfigurationException("'secret-group' names a group the regex lacks");
    }
    final SecretRule rule =
        new SecretRule(
            regex,
            pattern(toml, "path"),
            List.copyOf(TomlValues.optionalStrings(toml, "keywords")),
            entropy(toml),
            group,
            multiline(toml),
            SecretAllowlistParser.parse(toml.getArray("allowlists")),
            TomlValues.required(toml, "message"));
    return new SecretScanCheck(rule, global(toml));
  }

  private static Optional<Pattern> pattern(final TomlParseResult toml, final String key) {
    return toml.contains(key)
        ? Optional.of(SecretAllowlistParser.compile(TomlValues.required(toml, key), key))
        : Optional.empty();
  }

  private static Optional<Double> entropy(final TomlParseResult toml) {
    if (!toml.contains("entropy")) {
      return Optional.empty();
    }
    final Object value = toml.get("entropy");
    if (!(value instanceof Number number) || number.doubleValue() < 0) {
      throw new RuleConfigurationException("'entropy' must be a number, 0 or more");
    }
    return Optional.of(number.doubleValue());
  }

  private static boolean multiline(final TomlParseResult toml) {
    final String span = toml.contains(SPAN) ? TomlValues.required(toml, SPAN) : "line";
    if (!"line".equals(span) && !"text".equals(span)) {
      throw new RuleConfigurationException("'span' must be \"line\" or \"text\"");
    }
    return "text".equals(span);
  }

  private static List<SecretAllowlist> global(final TomlParseResult toml) {
    final String name = toml.getString("allowlist");
    if (name == null) {
      return List.of();
    }
    if (!DEFAULT_ALLOWLIST.equals(name)) {
      throw new RuleConfigurationException("'allowlist' must be \"" + DEFAULT_ALLOWLIST + "\"");
    }
    return List.of(SecretAllowlistFile.defaults());
  }
}
