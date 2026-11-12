package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.SecretAllowlist;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/**
 * The global allowlist of the Gitleaks default configuration, packaged as {@code
 * rules/secrets/secrets-allowlist.toml}. Its paths, regexes and stopwords apply to every secret
 * rule that names {@code allowlist = "default"}.
 */
final class SecretAllowlistFile {

  /** The classpath resource of the global allowlist. */
  static final String RESOURCE = "rules/secrets/secrets-allowlist.toml";

  private SecretAllowlistFile() {}

  /** Returns the global allowlist, read once. */
  static SecretAllowlist defaults() {
    return Holder.DEFAULTS;
  }

  private static SecretAllowlist read() {
    try (InputStream stream = SecretAllowlistFile.class.getResourceAsStream("/" + RESOURCE)) {
      if (stream == null) {
        throw new RuleConfigurationException(RESOURCE + " is missing from the Ward distribution");
      }
      final TomlParseResult toml =
          TomlValues.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      return new SecretAllowlist(
          patterns(toml.getArray("paths")),
          patterns(toml.getArray("regexes")),
          strings(toml.getArray("stopwords")).stream()
              .map(word -> word.toLowerCase(Locale.ROOT))
              .toList(),
          SecretAllowlist.Target.SECRET,
          false);
    } catch (final IOException exception) {
      throw new RuleConfigurationException(RESOURCE + " could not be read", exception);
    }
  }

  private static List<Pattern> patterns(final TomlArray array) {
    return strings(array).stream()
        .map(regex -> Pattern.compile(regex, Pattern.UNIX_LINES))
        .toList();
  }

  private static List<String> strings(final TomlArray array) {
    final List<String> values = new ArrayList<>();
    for (int index = 0; array != null && index < array.size(); index++) {
      values.add(TomlValues.stringAt(array, index, RESOURCE));
    }
    return values;
  }

  /** Holds the allowlist, read when first used. */
  private static final class Holder {
    private static final SecretAllowlist DEFAULTS = read();

    private Holder() {}
  }
}
