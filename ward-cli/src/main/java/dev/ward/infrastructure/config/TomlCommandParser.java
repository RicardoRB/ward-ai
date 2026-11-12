package dev.ward.infrastructure.config;

import dev.ward.domain.config.CommandLineTokenizer;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Parses the {@code command} of one {@code [checks.<name>]} table: a string or an array. */
final class TomlCommandParser {
  private static final String COMMAND = "command";
  private static final Set<String> COMMAND_LESS =
      Set.of(
          SupportedQualityGates.ANALYSIS,
          SupportedQualityGates.FORMAT,
          SupportedQualityGates.CRAP,
          SupportedQualityGates.COVERAGE,
          SupportedQualityGates.SECRETS,
          SupportedQualityGates.VULNERABILITIES,
          SupportedQualityGates.BUILD_POLICY,
          SupportedQualityGates.LICENSE);

  private TomlCommandParser() {}

  /** Returns the command; an empty command is allowed only for an analysis or format check. */
  static List<String> parse(final String where, final TomlTable gate, final String uses) {
    if (SupportedQualityGates.OPENAPI_LINT.equals(uses)) {
      return nativeCheck(where, gate, "openapi-lint runs Ward's own rules");
    }
    if (SupportedQualityGates.OPENAPI_DIFF.equals(uses)
        || SupportedQualityGates.OPENAPI_COMPAT.equals(uses)) {
      return nativeCheck(where, gate, uses + " compares the spec with Ward's own rules");
    }
    if (!gate.contains(COMMAND)) {
      return missing(where, uses);
    }
    final List<String> command = present(where, gate);
    if (command.isEmpty() || command.getFirst().isBlank()) {
      throw new WardException("'" + where + ".command' must not be empty");
    }
    return command;
  }

  /** The check runs inside Ward, so a command has nothing to run. */
  private static List<String> nativeCheck(
      final String where, final TomlTable gate, final String reason) {
    if (gate.contains(COMMAND)) {
      throw new WardException("'" + where + ".command' is not allowed: " + reason + ".");
    }
    return List.of();
  }

  private static List<String> missing(final String where, final String uses) {
    if (uses != null && COMMAND_LESS.contains(uses)) {
      return List.of();
    }
    throw new WardException("'" + where + ".command' is required");
  }

  private static List<String> present(final String where, final TomlTable gate) {
    if (gate.isString(COMMAND)) {
      return commandString(where, gate);
    }
    if (gate.isArray(COMMAND)) {
      return commandArray(where, gate);
    }
    throw new WardException("'" + where + ".command' must be a string or an array of strings");
  }

  private static List<String> commandString(final String where, final TomlTable gate) {
    final String value = gate.getString(COMMAND);
    if (value == null) {
      throw new WardException("'" + where + ".command' must be a string");
    }
    return CommandLineTokenizer.tokenize(value);
  }

  private static List<String> commandArray(final String where, final TomlTable gate) {
    final TomlArray array = gate.getArray(COMMAND);
    if (array == null) {
      throw new WardException("'" + where + ".command' must be an array of strings");
    }
    final List<String> command = new ArrayList<>(array.size());
    for (int i = 0; i < array.size(); i++) {
      if (!array.isString(i)) {
        throw new WardException("'" + where + ".command' array must contain only strings");
      }
      command.add(array.getString(i));
    }
    return command;
  }
}
