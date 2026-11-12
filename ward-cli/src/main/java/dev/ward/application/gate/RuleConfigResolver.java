package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RuleOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Turns a {@link RuleConfigFile} and the rules that exist into the {@link RuleSet} to run. Every
 * problem is collected, so one run reports them all; with any problem no rule is returned.
 */
final class RuleConfigResolver {
  private static final Logger LOGGER = Logger.getLogger(RuleConfigResolver.class.getName());
  private static final String ERROR = "error";
  private static final String WARNING = "warning";
  private static final String SEVERITY = "severity";
  private static final String EXCLUDE_PATHS = "exclude-paths";

  private final RuleConfigFile file;
  private final List<RuleDefinition> catalog;
  private final int javaRelease;
  private final RuleProblems problems;
  private final Map<String, List<Pattern>> excludes = new LinkedHashMap<>();
  private final Map<String, RuleDefinition> configured = new LinkedHashMap<>();

  private RuleConfigResolver(
      final RuleConfigFile file, final List<RuleDefinition> catalog, final int javaRelease) {
    this.file = file;
    this.catalog = catalog;
    this.javaRelease = javaRelease;
    this.problems = new RuleProblems(file.name(), file.problems());
  }

  /** Resolves {@code file} against {@code catalog}; the result has problems or rules, not both. */
  static RuleSet resolve(
      final RuleConfigFile file, final List<RuleDefinition> catalog, final int javaRelease) {
    return new RuleConfigResolver(file, catalog, javaRelease).run();
  }

  private RuleSet run() {
    final Map<String, RuleDefinition> selected =
        new RuleSelector(file.select(), catalog, problems).selected();
    selected.forEach(
        (id, rule) -> {
          if (rule.runsOn(javaRelease)) {
            configured.put(id, JavaRelease.withDefault(rule, javaRelease));
          }
        });
    file.rules().forEach((id, table) -> tune(id, table, selected));
    if (problems.isEmpty()) {
      return new RuleSet(new ArrayList<>(configured.values()), excludes, List.of());
    }
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=rule-config problems=" + problems.messages().size());
    }
    return new RuleSet(List.of(), Map.of(), problems.messages());
  }

  private void tune(
      final String id,
      final Map<String, Object> table,
      final Map<String, RuleDefinition> selected) {
    if (id.startsWith("ward:build/")) {
      return;
    }
    final String where = file.header(id);
    final Optional<RuleDefinition> rule =
        catalog.stream().filter(r -> r.id().equals(id)).findFirst();
    if (rule.isPresent() && selected.containsKey(id)) {
      tuneSelected(rule.get(), where, table);
    } else if (rule.isPresent()) {
      problems.add(
          where, "", "the rule is not selected; add it to [select] include or remove this table");
    } else {
      problems.add(where, "", "unknown rule id");
    }
  }

  private void tuneSelected(
      final RuleDefinition rule, final String where, final Map<String, Object> table) {
    final Map<String, Object> options = new LinkedHashMap<>();
    AnalysisSeverity severity = rule.severity();
    for (final Map.Entry<String, Object> entry : table.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (SEVERITY.equals(key)) {
        severity = severity(where, value, severity);
      } else if (EXCLUDE_PATHS.equals(key)) {
        excludePaths(rule.id(), where, value);
      } else if (option(rule, where, key, value)) {
        options.put(key, value);
      }
    }
    if (rule.option(JavaRelease.OPTION).isPresent() && !options.containsKey(JavaRelease.OPTION)) {
      options.put(JavaRelease.OPTION, Long.valueOf(javaRelease));
    }
    apply(rule, where, severity, options);
  }

  private AnalysisSeverity severity(
      final String where, final Object value, final AnalysisSeverity current) {
    if (ERROR.equals(value)) {
      return AnalysisSeverity.ERROR;
    }
    if (WARNING.equals(value)) {
      return AnalysisSeverity.WARNING;
    }
    problems.add(
        where, SEVERITY, "expected \"" + ERROR + "\" or \"" + WARNING + "\", got " + show(value));
    return current;
  }

  private void excludePaths(final String id, final String where, final Object value) {
    final List<Pattern> matchers = new ArrayList<>();
    if (value instanceof List<?> globs && globs.stream().allMatch(String.class::isInstance)) {
      for (final Object glob : globs) {
        compile(where, (String) glob).ifPresent(matchers::add);
      }
      excludes.put(id, matchers);
    } else {
      problems.add(where, EXCLUDE_PATHS, "expected a list of glob strings, got " + show(value));
    }
  }

  private Optional<Pattern> compile(final String where, final String glob) {
    try {
      return Optional.of(ExcludeGlob.compile(glob));
    } catch (final IllegalArgumentException exception) {
      problems.add(where, EXCLUDE_PATHS, "invalid glob \"" + glob + "\"");
      return Optional.empty();
    }
  }

  /** Returns whether {@code key} is a declared option whose value is allowed. */
  private boolean option(
      final RuleDefinition rule, final String where, final String key, final Object value) {
    final Optional<RuleOption> option = rule.option(key);
    if (option.isEmpty()) {
      final List<String> declared = rule.options().stream().map(RuleOption::name).toList();
      problems.add(
          where,
          key,
          "unknown option; this rule has "
              + (declared.isEmpty() ? "no options" : "options " + declared)
              + ", besides severity and exclude-paths");
      return false;
    }
    final Optional<String> problem = option.get().problem(value);
    problem.ifPresent(detail -> problems.add(where, key, detail));
    return problem.isEmpty();
  }

  private void apply(
      final RuleDefinition rule,
      final String where,
      final AnalysisSeverity severity,
      final Map<String, Object> options) {
    try {
      final RuleDefinition tuned = RuleOptions.apply(rule, options);
      if (!rule.runsOn(javaRelease)) {
        return;
      }
      configured.put(
          rule.id(),
          new RuleDefinition(
              tuned.id(),
              tuned.languages(),
              tuned.capability(),
              severity,
              tuned.body(),
              tuned.category(),
              tuned.presets(),
              tuned.options(),
              tuned.minJavaRelease()));
    } catch (final IllegalArgumentException exception) {
      problems.add(where, "", exception.getMessage());
    }
  }

  private static String show(final Object value) {
    return value instanceof String text ? "\"" + text + "\"" : String.valueOf(value);
  }
}
