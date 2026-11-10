package dev.ward.infrastructure.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads the {@code [rules]} table of {@code ward-rules.toml}. A rule table is written {@code
 * [rules.<group>.<name>]} and names the rule {@code ward:<group>/<name>}. A table named by the
 * quoted rule id is a problem that names the form to write instead.
 */
final class RuleTableReader {
  private static final String RULES = "rules";
  private static final String RULE_PREFIX = "ward:";

  private final String name;
  private final List<String> problems;

  RuleTableReader(final String name, final List<String> problems) {
    this.name = name;
    this.problems = problems;
  }

  /** Returns the rule tables by rule id, in file order. Problems go to the shared list. */
  Map<String, Map<String, Object>> read(final TomlParseResult toml) {
    final Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
    final TomlTable table = toml.isTable(RULES) ? toml.getTable(RULES) : null;
    if (toml.contains(RULES) && table == null) {
      problem("", RULES, "expected a table of rule tables");
      return rules;
    }
    for (final String key : table == null ? Set.<String>of() : table.keySet()) {
      if (key.contains(":")) {
        quotedRule(key);
      } else {
        group(table, key, rules);
      }
    }
    return rules;
  }

  /** Reports {@code [rules."ward:<group>/<name>"]}, which is not a rule table name. */
  private void quotedRule(final String id) {
    final String dotted = id.substring(id.indexOf(':') + 1).replace('/', '.');
    problems.add(
        name
            + ": [rules.\""
            + id
            + "\"]: name the table [rules.<language>.<rule-name>]; write [rules."
            + dotted
            + "]");
  }

  /** Reads {@code [rules.<group>.<name>]} as {@code ward:<group>/<name>}. */
  private void group(
      final TomlTable table, final String group, final Map<String, Map<String, Object>> rules) {
    final TomlTable entries = table.isTable(List.of(group)) ? table.getTable(List.of(group)) : null;
    if (entries == null) {
      problem("", RULES, "[rules." + group + "] must be a table");
      return;
    }
    for (final String rule : entries.keySet()) {
      final TomlTable options =
          entries.isTable(List.of(rule)) ? entries.getTable(List.of(rule)) : null;
      if (options == null) {
        problem("[rules." + group + "]", rule, "must be a rule table");
      } else {
        rules.put(RULE_PREFIX + group + "/" + rule, TomlTree.values(options));
      }
    }
  }

  private void problem(final String table, final String key, final String detail) {
    final String where = (table.isEmpty() ? "" : " " + table) + (key.isEmpty() ? "" : " " + key);
    problems.add(name + ":" + where + ": " + detail);
  }
}
