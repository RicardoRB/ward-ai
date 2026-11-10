package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Validates the options of a taint specification entry: the argument positions a sink guards, and
 * the inputs a propagator names.
 */
final class TaintSpecOptions {

  private static final String ARGUMENTS = "arguments";
  private static final String FROM = "from";
  private static final String RECEIVER = "receiver";
  private static final String SINKS = "sinks";

  private TaintSpecOptions() {}

  /** A sink names the argument positions it guards, and no other entry may name any. */
  static List<Integer> arguments(final String where, final String table, final TomlTable entry) {
    if (!entry.contains(ARGUMENTS)) {
      return missingArguments(where, table);
    }
    if (!SINKS.equals(table)) {
      throw new RuleConfigurationException(
          where + " names argument positions, which only sinks take");
    }
    return positions(where, entry.getArray(ARGUMENTS));
  }

  private static List<Integer> missingArguments(final String where, final String table) {
    if (SINKS.equals(table)) {
      throw new RuleConfigurationException(where + " names no argument positions");
    }
    return List.of();
  }

  private static List<Integer> positions(final String where, final TomlArray array) {
    if (array == null || array.isEmpty()) {
      throw new RuleConfigurationException(where + " names no argument positions");
    }
    final List<Integer> positions = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      if (!array.isLong(index) || array.getLong(index) < 0) {
        throw new RuleConfigurationException(
            where + " has an argument position that is not 0 or more");
      }
      positions.add((int) array.getLong(index));
    }
    return positions;
  }

  /** A propagator's inputs are argument positions or the receiver. */
  static void from(final String where, final TomlTable entry) {
    if (!entry.contains(FROM)) {
      return;
    }
    final TomlArray array = entry.getArray(FROM);
    if (array == null) {
      throw new RuleConfigurationException(where + " has a 'from' that is not a list");
    }
    for (int index = 0; index < array.size(); index++) {
      final boolean position = array.isLong(index) && array.getLong(index) >= 0;
      final boolean receiver = array.isString(index) && RECEIVER.equals(array.getString(index));
      if (!position && !receiver) {
        throw new RuleConfigurationException(
            where + " has a 'from' input that is neither a position nor 'receiver'");
      }
    }
  }
}
