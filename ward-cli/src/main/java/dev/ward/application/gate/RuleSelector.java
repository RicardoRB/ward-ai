package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies the {@code [select]} table: the rules of the preset, kept to the listed languages and
 * categories, plus {@code include}, minus {@code exclude}. The two lists name a rule {@code
 * <language>.<rule-name>}, as a rule table does. A value that names nothing, or a rule written as
 * its id, is reported to {@link RuleProblems}.
 */
final class RuleSelector {
  private static final String SELECT = "[select]";
  private static final String DEFAULT_PRESET = "standard";
  private static final String RULE_PREFIX = "ward:";
  private static final char ID_MARK = ':';
  private static final String BUILD_PREFIX = "build.";

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
      if (rule.capability() == RuleCapability.TEXT) {
        chosen.put(rule.id(), rule);
      } else if (preset.selects(rule.presets())
          && (languages.isEmpty() || languages.stream().anyMatch(rule::covers))
          && (categories.isEmpty() || categories.contains(rule.category()))) {
        chosen.put(rule.id(), rule);
      }
    }
    for (final RuleDefinition rule : known("include", select.include())) {
      chosen.put(rule.id(), rule);
    }
    known("exclude", select.exclude()).forEach(rule -> chosen.remove(rule.id()));
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
    final Set<AnalysisLanguage> languages = new java.util.LinkedHashSet<>();
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
    final Set<String> known =
        catalog.stream().map(RuleDefinition::category).collect(Collectors.toSet());
    final Set<String> categories = new java.util.LinkedHashSet<>();
    for (final String category : select.categories()) {
      if (known.contains(category)) {
        categories.add(category);
      } else {
        problems.add(
            SELECT,
            "categories",
            "unknown category \""
                + category
                + "\"; expected one of "
                + known.stream().filter(name -> !name.isEmpty()).sorted().toList());
      }
    }
    return categories;
  }

  private List<RuleDefinition> known(final String key, final List<String> names) {
    final List<RuleDefinition> rules = new ArrayList<>();
    for (final String name : names) {
      if (name.startsWith(BUILD_PREFIX)) {
        continue;
      }
      if (name.indexOf(ID_MARK) >= 0) {
        problems.add(SELECT, key, writeDotted(name));
        continue;
      }
      final String id = RULE_PREFIX + name.replaceFirst("\\.", "/");
      catalog.stream()
          .filter(rule -> rule.id().equals(id))
          .findFirst()
          .ifPresentOrElse(
              rules::add, () -> problems.add(SELECT, key, "unknown rule \"" + name + "\""));
    }
    return rules;
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
