package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleGroup;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Applies the {@code [select]} table: the rules of the preset, kept to the listed languages and
 * category groups, plus {@code include}, minus {@code exclude}. The two lists name a rule {@code
 * <language>.<rule-name>} or a group {@code <family>} or {@code <family>/<subcategory>}; a value
 * with a dot is a rule. A value that names nothing, or a rule written as its id, is reported to
 * {@link RuleProblems}.
 */
final class RuleSelector {
  private static final String SELECT = "[select]";
  private static final String DEFAULT_PRESET = "standard";
  private static final String RULE_PREFIX = "ward:";
  private static final char ID_MARK = ':';
  private static final char RULE_MARK = '.';

  private final RuleConfigFile.Select select;
  private final List<RuleDefinition> catalog;
  private final RuleProblems problems;

  RuleSelector(
      final RuleConfigFile.Select select,
      final List<RuleDefinition> catalog,
      final RuleProblems problems) {
    this.select = select;
    this.catalog = catalog;
    this.problems = problems;
  }

  /** Returns the selected rules by id, in catalog order. */
  Map<String, RuleDefinition> selected() {
    final RulePreset preset = preset();
    final Set<AnalysisLanguage> languages = languages();
    final Set<String> categories = categories();
    final Map<String, RuleDefinition> chosen = new LinkedHashMap<>();
    for (final RuleDefinition rule : catalog) {
      if (preset.selects(rule.presets())
          && (languages.isEmpty() || languages.stream().anyMatch(rule::covers))
          && (categories.isEmpty() || matchesAny(categories, rule.category()))) {
        chosen.put(rule.id(), rule);
      }
    }
    for (final RuleDefinition rule : resolve("include", select.include(), languages)) {
      chosen.put(rule.id(), rule);
    }
    resolve("exclude", select.exclude(), Set.of()).forEach(rule -> chosen.remove(rule.id()));
    return chosen;
  }

  private RulePreset preset() {
    final String key = select.preset() == null ? DEFAULT_PRESET : select.preset();
    return RulePreset.byKey(key)
        .orElseGet(
            () -> {
              problems.add(
                  SELECT,
                  "preset",
                  "expected one of "
                      + List.of(RulePreset.values()).stream().map(RulePreset::key).toList()
                      + ", got \""
                      + key
                      + "\"");
              return RulePreset.NONE;
            });
  }

  private Set<AnalysisLanguage> languages() {
    final Set<AnalysisLanguage> languages = new LinkedHashSet<>();
    for (final String name : select.languages()) {
      try {
        languages.add(AnalysisLanguage.valueOf(name.toUpperCase(Locale.ROOT)));
      } catch (final IllegalArgumentException exception) {
        problems.add(
            SELECT,
            "languages",
            "unknown language \""
                + name
                + "\"; expected "
                + List.of(AnalysisLanguage.values()).stream()
                    .map(language -> language.name().toLowerCase(Locale.ROOT))
                    .toList());
      }
    }
    return languages;
  }

  private Set<String> categories() {
    final Set<String> categories = new LinkedHashSet<>();
    for (final String group : select.categories()) {
      if (isKnownGroup(group)) {
        categories.add(group);
      } else {
        problems.add(SELECT, "categories", unknownGroup(group));
      }
    }
    return categories;
  }

  private boolean isKnownGroup(final String group) {
    return catalog.stream().anyMatch(rule -> RuleGroup.matches(group, rule.category()));
  }

  private static boolean matchesAny(final Set<String> groups, final String category) {
    return groups.stream().anyMatch(group -> RuleGroup.matches(group, category));
  }

  private String unknownGroup(final String group) {
    return "unknown group \""
        + group
        + "\"; expected one of "
        + RuleGroup.names(catalog.stream().map(RuleDefinition::category).toList());
  }

  private List<RuleDefinition> resolve(
      final String key, final List<String> names, final Set<AnalysisLanguage> languages) {
    final List<RuleDefinition> rules = new ArrayList<>();
    for (final String name : names) {
      if (name.indexOf(ID_MARK) >= 0) {
        problems.add(SELECT, key, writeDotted(name));
      } else if (name.indexOf(RULE_MARK) >= 0) {
        rule(key, name).ifPresent(rules::add);
      } else {
        rules.addAll(group(key, name, languages));
      }
    }
    return rules;
  }

  private Optional<RuleDefinition> rule(final String key, final String name) {
    final String id = RULE_PREFIX + name.replaceFirst("\\.", "/");
    final Optional<RuleDefinition> rule =
        catalog.stream().filter(candidate -> candidate.id().equals(id)).findFirst();
    if (rule.isEmpty()) {
      problems.add(SELECT, key, "unknown rule \"" + name + "\"");
    }
    return rule;
  }

  /** The rules of a group; a group that matches no rule is reported, and languages narrow it. */
  private List<RuleDefinition> group(
      final String key, final String name, final Set<AnalysisLanguage> languages) {
    if (!isKnownGroup(name)) {
      problems.add(SELECT, key, unknownGroup(name));
      return List.of();
    }
    return catalog.stream()
        .filter(rule -> RuleGroup.matches(name, rule.category()))
        .filter(rule -> languages.isEmpty() || languages.stream().anyMatch(rule::covers))
        .toList();
  }

  /** The problem of a rule written as its id, {@code ward:<language>/<rule-name>}. */
  private static String writeDotted(final String id) {
    return "\""
        + id
        + "\": name the rule <language>.<rule-name>; write \""
        + id.substring(id.indexOf(ID_MARK) + 1).replace('/', '.')
        + "\"";
  }
}
