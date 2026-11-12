package dev.ward.application.gate.buildpolicy;

import dev.ward.application.buildpolicy.BuildPolicySetting;
import dev.ward.application.gate.RuleConfigFile;
import dev.ward.domain.buildpolicy.BuildRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads the {@code [rules.build.<name>]} tables: severity, options and what is refused. */
final class BuildPolicyTables {
  private static final String SEVERITY = "severity";
  private static final String EXCLUDE_PATHS = "exclude-paths";
  private static final String WARNING = "warning";
  private static final String ERROR = "error";
  private static final Pattern DOTTED = Pattern.compile("\\d+(\\.\\d+)*");

  private final RuleConfigFile file;
  private final ConfigProblems problems;

  BuildPolicyTables(final RuleConfigFile file, final ConfigProblems problems) {
    this.file = file;
    this.problems = problems;
  }

  /** Returns the settings of the {@code selected} rules and reports tables that do not fit. */
  List<BuildPolicySetting> tune(final Set<BuildRule> selected) {
    final Map<BuildRule, Map<String, Object>> tables = tables();
    final List<BuildPolicySetting> settings =
        selected.stream().map(rule -> setting(rule, tables.getOrDefault(rule, Map.of()))).toList();
    tables.keySet().stream()
        .filter(rule -> !selected.contains(rule))
        .forEach(
            rule ->
                problems.add(
                    header(rule),
                    "",
                    "the rule is not selected; add it to [select] include or remove this table"));
    return settings;
  }

  private Map<BuildRule, Map<String, Object>> tables() {
    final Map<BuildRule, Map<String, Object>> tables = new LinkedHashMap<>();
    file.rules()
        .forEach(
            (id, table) -> {
              if (id.startsWith(BuildRule.PREFIX)) {
                final String name = id.substring(BuildRule.PREFIX.length());
                BuildRule.byName(name)
                    .ifPresentOrElse(
                        rule -> tables.put(rule, table),
                        () -> problems.add("[rules.build." + name + "]", "", "unknown rule id"));
              }
            });
    return tables;
  }

  private BuildPolicySetting setting(final BuildRule rule, final Map<String, Object> table) {
    boolean warning = false;
    final Map<String, String> options = new LinkedHashMap<>();
    for (final Map.Entry<String, Object> entry : table.entrySet()) {
      final String key = entry.getKey();
      if (SEVERITY.equals(key)) {
        warning = warning(rule, entry.getValue());
      } else if (EXCLUDE_PATHS.equals(key)) {
        problems.add(
            header(rule), key, "build rules accept no suppression, so exclude-paths is refused");
      } else {
        option(rule, key, entry.getValue()).ifPresent(text -> options.put(key, text));
      }
    }
    return new BuildPolicySetting(rule, warning, options);
  }

  private boolean warning(final BuildRule rule, final Object value) {
    if (WARNING.equals(value)) {
      return true;
    }
    if (!ERROR.equals(value)) {
      problems.add(header(rule), SEVERITY, "expected \"error\" or \"warning\", got " + value);
    }
    return false;
  }

  private Optional<String> option(final BuildRule rule, final String key, final Object value) {
    if (!rule.options().contains(key)) {
      problems.add(
          header(rule),
          key,
          "unknown option; this rule has "
              + (rule.options().isEmpty() ? "no options" : "options " + rule.options())
              + ", besides severity");
      return Optional.empty();
    }
    if (value instanceof String text && DOTTED.matcher(text).matches()) {
      return Optional.of(text);
    }
    problems.add(header(rule), key, "expected a dotted version such as \"3.9\", got " + value);
    return Optional.empty();
  }

  private static String header(final BuildRule rule) {
    return "[rules.build." + rule.ruleName() + "]";
  }
}
