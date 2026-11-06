package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Formats the table rows of the generated rule reference. */
final class RuleReferenceRows {

  static final String ISSUES = "https://github.com/RicardoRB/ward-ai/issues/";

  private RuleReferenceRows() {}

  /** Returns the row of one implemented rule, with the options its definition declares. */
  static String implementedRow(
      final CatalogRow row, final AnalysisLanguage language, final List<RuleDefinition> rules) {
    final RuleDefinition rule = definition(row.idFor(language), rules);
    return String.join(
        " | ",
        "| `" + rule.id() + "`",
        rule.category(),
        presets(rule),
        options(rule),
        RuleReferenceTools.counterparts(row) + " |");
  }

  /** Returns the row of one catalog row on a file page. */
  static String fileRow(final CatalogRow row, final List<RuleDefinition> definitions) {
    final List<String> cells = new ArrayList<>();
    cells.add("| `" + row.name() + "`");
    cells.add(category(row, definitions));
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      cells.add(cell(row.status(language)));
    }
    cells.add(RuleReferenceTools.counterparts(row) + " |");
    return String.join(" | ", cells);
  }

  /** Returns the header and separator rows of a file page table. */
  static List<String> fileHeader() {
    final List<String> header = new ArrayList<>();
    header.add("Rule");
    header.add("Category");
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      header.add(CatalogRow.key(language));
    }
    header.add("Counterparts");
    final String separator = "|" + "---|".repeat(header.size());
    return List.of("| " + String.join(" | ", header) + " |", separator);
  }

  private static RuleDefinition definition(final String id, final List<RuleDefinition> rules) {
    return rules.stream().filter(rule -> rule.id().equals(id)).findFirst().orElseThrow();
  }

  private static String presets(final RuleDefinition rule) {
    final Set<RulePreset> presets = rule.presets();
    return presets.stream()
        .sorted(Comparator.comparing(RulePreset::ordinal))
        .map(RulePreset::key)
        .collect(Collectors.joining(", "));
  }

  private static String options(final RuleDefinition rule) {
    if (rule.options().isEmpty()) {
      return "none";
    }
    return rule.options().stream()
        .map(RuleOption::name)
        .map(name -> "`" + name + "`")
        .collect(Collectors.joining(", "));
  }

  private static String category(final CatalogRow row, final List<RuleDefinition> definitions) {
    if (!row.category().isEmpty()) {
      return row.category();
    }
    return definitions.stream()
        .filter(rule -> rule.languages().stream().anyMatch(l -> rule.id().equals(row.idFor(l))))
        .map(RuleDefinition::category)
        .findFirst()
        .orElse("");
  }

  private static String cell(final LanguageStatus status) {
    return switch (status.status()) {
      case IMPLEMENTED -> "implemented";
      case PLANNED -> "[" + status.issue() + "](" + ISSUES + status.issue().substring(1) + ")";
      case NOT_APPLICABLE -> "n/a";
    };
  }
}
