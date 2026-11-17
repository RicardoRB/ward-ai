package dev.ward.infrastructure.config;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.Set;
import org.tomlj.TomlTable;

/**
 * Parses the generic {@code [checks.<name>.prompts]} table, which turns on the scan of agent
 * prompts for a check. The table holds {@code enabled} and nothing else, and only the types in
 * {@link #SUPPORTED} scan prompts; a table on another check is an error, never ignored.
 */
final class TomlPromptsParser {
  private static final String PROMPTS = "prompts";
  private static final String ENABLED = "enabled";
  private static final Set<String> SUPPORTED = Set.of(SupportedQualityGates.SECRETS);

  private TomlPromptsParser() {}

  /** Returns whether the prompts of {@code gate} are scanned; false when the table is absent. */
  static boolean parse(final String where, final String type, final TomlTable gate) {
    if (!gate.contains(PROMPTS)) {
      return false;
    }
    final String key = where + "." + PROMPTS;
    if (!SUPPORTED.contains(type)) {
      throw new WardException(
          "'" + key + "': check '" + where.substring(where.indexOf('.') + 1)
              + "' does not scan prompts; only these checks do: " + SUPPORTED
      );
    }
    if (!gate.isTable(PROMPTS)) {
      throw new WardException("'" + key + "' must be a table");
    }
    final TomlTable table = gate.getTable(PROMPTS);
    for (final String name : table.keySet()) {
      if (!ENABLED.equals(name)) {
        throw new WardException("'" + key + "." + name + "' is not a key of a prompts table");
      }
    }
    if (table.contains(ENABLED) && !table.isBoolean(ENABLED)) {
      throw new WardException("'" + key + "." + ENABLED + "' must be true or false");
    }
    return Boolean.TRUE.equals(table.getBoolean(ENABLED));
  }
}
