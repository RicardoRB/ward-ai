package dev.ward.infrastructure.rule.catalog;

import java.util.List;
import java.util.Map;

/**
 * One implemented Java rule to compare with the tools it replaces.
 *
 * @param name the fixture directory name, the last part of the rule id
 * @param counterparts the ids of the tools' rules, by tool name
 * @param expected the {@code line:column} positions of {@code expected.txt}
 */
record ParityTarget(String name, Map<String, List<String>> counterparts, List<String> expected) {

  ParityTarget {
    counterparts = Map.copyOf(counterparts);
    expected = List.copyOf(expected);
  }

  /** Returns the ids the tool has for this rule, empty when it has none. */
  List<String> idsOf(final String tool) {
    return counterparts.getOrDefault(tool, List.of());
  }
}
