package dev.ward.infrastructure.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads the {@code [rules]} table of {@code ward-rules.toml}. A rule table is written either as
 * {@code [rules."ward:<group>/<name>"]} or as {@code [rules.<group>.<name>]}, and both name the
 * rule {@code ward:<group>/<name>}. Configuring one rule in both forms is a problem that names both
 * tables; the first table is kept.
 */
final class RuleTableReader {
  private static final String RULES = "rules";
  private static final String RULE_PREFIX = "ward:";

  private final String name;
  private final List<String> problems;
  private final Map<String, String> headers = new LinkedHashMap<>();

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
        quotedRule(table, key, rules);
      } else {
        group(table, key, rules);
      }
    }
    return rules;
  }

  /** Returns the table header of each rule as written, by rule id. */
  Map<String, String> headers() {
    return Map.copyOf(headers);
  }

  /** Reads {@code [rules."ward:<group>/<name>"]}. */
  private void quotedRule(
      final TomlTable table, final String id, final Map<String, Map<String, Object>> rules) {
    final TomlTable rule = table.isTable(List.of(id)) ? table.getTable(List.of(id)) : null;
    if (rule == null) {
      problem("", RULES, "[rules.\"" + id + "\"] must be a table");
    } else {
      add(rules, id, "[rules.\"" + id + "\"]", rule);
    }
  }

  /** Reads {@code [rules.<group>.<name>]} as {@code ward:<group>/<name>}. */
  private void group(
      final TomlTable table, final String group, final Map<String, Map<String, Object>> rules) {
    final TomlTable entries = table.isTable(List.of(group)) ? table.getTable(List.of(group)) : null;
    if (entries == null) {
      problem("", RULES, "[rules.\"" + group + "\"] must be a table");
      return;
    }
    for (final String rule : entries.keySet()) {
      if (entries.isTable(List.of(rule))) {
        add(
            rules,
            RULE_PREFIX + group + "/" + rule,
            "[rules." + group + "." + rule + "]",
            entries.getTable(List.of(rule)));
      } else {
        problem("[rules." + group + "]", rule, "must be a rule table");
      }
    }
  }

  /** Adds the rule unless another table already configures it; the first table is kept. */
  private void add(
      final Map<String, Map<String, Object>> rules,
      final String id,
      final String header,
      final TomlTable rule) {
    if (headers.containsKey(id)) {
      problems.add(
          name
              + ": "
              + headers.get(id)
              + " and "
              + header
              + " configure the same rule; keep one table");
      return;
    }
    headers.put(id, header);
    rules.put(id, TomlTree.values(rule));
  }

  private void problem(final String table, final String key, final String detail) {
    final String where = (table.isEmpty() ? "" : " " + table) + (key.isEmpty() ? "" : " " + key);
    problems.add(name + ":" + where + ": " + detail);
  }
}
