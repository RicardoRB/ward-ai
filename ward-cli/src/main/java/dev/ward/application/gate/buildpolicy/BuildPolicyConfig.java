package dev.ward.application.gate.buildpolicy;

import dev.ward.application.buildpolicy.BuildPolicySetting;
import dev.ward.application.gate.RuleConfigFile;
import dev.ward.domain.buildpolicy.BuildRule;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the {@code ward:build/*} part of {@code ward-rules.toml} into rule settings. Every problem
 * is collected, so one run reports them all; with any problem no rule runs. Ids of other gates are
 * left alone.
 */
final class BuildPolicyConfig {
  private static final String PREFIX = "build.";
  private static final String STRICT = "strict";
  private static final List<String> PRESETS = List.of("none", "standard", STRICT);

  private final RuleConfigFile file;
  private final ConfigProblems problems;

  private BuildPolicyConfig(final RuleConfigFile file) {
    this.file = file;
    this.problems = new ConfigProblems(file.name(), file.problems());
  }

  /** The settings to run, or the problems that stop the run. */
  record Resolved(List<BuildPolicySetting> settings, List<String> problems) {
    Resolved {
      settings = List.copyOf(settings);
      problems = List.copyOf(problems);
    }
  }

  /** Returns the settings of a check with no configuration file: the {@code strict} rules. */
  static List<BuildPolicySetting> defaults() {
    return List.of(BuildRule.values()).stream()
        .filter(BuildRule::strict)
        .map(rule -> new BuildPolicySetting(rule, false, Map.of()))
        .toList();
  }

  /** Resolves {@code file}. */
  static Resolved resolve(final RuleConfigFile file) {
    return new BuildPolicyConfig(file).run();
  }

  private Resolved run() {
    final Set<BuildRule> selected = selected();
    final BuildPolicyTables tables = new BuildPolicyTables(file, problems);
    final List<BuildPolicySetting> settings = tables.tune(selected);
    return new Resolved(problems.isEmpty() ? settings : List.of(), problems.messages());
  }

  private Set<BuildRule> selected() {
    final String preset = file.select().preset() == null ? "standard" : file.select().preset();
    if (!PRESETS.contains(preset)) {
      problems.add("[select]", "preset", "expected one of " + PRESETS + ", got \"" + preset + "\"");
    }
    final Set<BuildRule> chosen = new LinkedHashSet<>();
    if (STRICT.equals(preset)) {
      List.of(BuildRule.values()).stream().filter(BuildRule::strict).forEach(chosen::add);
    }
    named("include", file.select().include()).forEach(chosen::add);
    named("exclude", file.select().exclude()).forEach(chosen::remove);
    return chosen;
  }

  private List<BuildRule> named(final String key, final List<String> names) {
    final List<BuildRule> rules = new ArrayList<>();
    for (final String name : names) {
      if (name.startsWith(PREFIX)) {
        final Optional<BuildRule> rule = BuildRule.byName(name.substring(PREFIX.length()));
        rule.ifPresentOrElse(
            rules::add, () -> problems.add("[select]", key, "unknown rule \"" + name + "\""));
      }
    }
    return rules;
  }
}
