package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates the rule reference of the website from the catalog and the rule definitions: an index
 * page with the counts per tool and every implemented rule with its options, and one page per
 * catalog file with every row, its status per language and its counterparts per tool. The page
 * texts are the only output, so a test can compare them with the committed pages.
 */
final class RuleReference {

  /** The index page, relative to {@code website/src/content/docs}. */
  static final String INDEX = "reference/rules.mdx";

  private static final String GENERATED =
      "{/* Generated from the rule catalog by RuleReferenceTest. Do not edit by hand. */}";

  private RuleReference() {}

  /** Returns every page, keyed by its path under {@code website/src/content/docs}. */
  static Map<String, String> pages(
      final RuleCatalog catalog,
      final List<RuleDefinition> definitions,
      final Map<String, Map<String, String>> outOfScope) {
    final Map<String, List<CatalogRow>> byFile =
        catalog.rows().stream()
            .collect(
                Collectors.groupingBy(
                    RuleReference::pageName, LinkedHashMap::new, Collectors.toList()));
    final Map<String, String> pages = new LinkedHashMap<>();
    pages.put(INDEX, index(byFile));
    byFile.forEach(
        (name, rows) ->
            pages.put("reference/rules/" + name + ".mdx", filePage(name, rows, definitions)));
    return pages;
  }

  private static String pageName(final CatalogRow row) {
    final String source = row.source();
    return source.substring(source.lastIndexOf('/') + 1, source.length() - ".toml".length());
  }

  private static String index(final Map<String, List<CatalogRow>> byFile) {
    final List<String> lines = new ArrayList<>();
    lines.addAll(
        frontMatter(
            "Rule reference",
            "Every Ward rule, the rules it replaces in other tools, and what is planned."));
    lines.add(GENERATED);
    lines.add("");
    lines.add(
        "One catalog maps every rule of the tools that Ward replaces to a Ward rule, or lists it as"
            + " out of scope with a reason. A rule is `implemented` when a rule definition ships for"
            + " the language, `planned` when a later step of roadmap [#306]("
            + RuleReferenceRows.ISSUES
            + "306) or [#348]("
            + RuleReferenceRows.ISSUES
            + "348) implements it, and `n/a` when the concept does not apply to the language.");
    lines.add("");
    lines.add("## Tools");
    lines.add("");
    lines.add("| Tool | Pinned version |");
    lines.add("|---|---|");
    for (final String tool : CatalogTools.ALL) {
      lines.add(RuleReferenceTools.versionRow(tool));
    }
    lines.add("");
    lines.add("## Catalog pages");
    lines.add("");
    lines.add(
        "Each page lists the rules of one catalog file, the Ward rules implemented for them, and");
    lines.add("their counterparts in each tool.");
    lines.add("");
    byFile.keySet().forEach(name -> lines.add("- [" + name + "](/reference/rules/" + name + "/)"));
    return String.join("\n", lines) + "\n";
  }

  private static String filePage(
      final String name, final List<CatalogRow> rows, final List<RuleDefinition> definitions) {
    final List<String> lines = new ArrayList<>();
    lines.addAll(
        frontMatter(name, "Rules of the catalog file " + name + " and their counterparts."));
    lines.add(GENERATED);
    lines.add("");
    lines.add("[Rule reference](/reference/rules/)");
    lines.add("");
    lines.addAll(RuleReferenceRows.fileHeader());
    for (final CatalogRow row : rows) {
      lines.add(RuleReferenceRows.fileRow(row, definitions));
    }
    final List<String> implemented = implementedRows(rows, definitions);
    if (!implemented.isEmpty()) {
      lines.add("");
      lines.add("## Implemented rules");
      lines.add("");
      lines.add("| Rule | Category | Presets | Options | Counterparts |");
      lines.add("|---|---|---|---|---|");
      lines.addAll(implemented);
    }
    return String.join("\n", lines) + "\n";
  }

  private static List<String> implementedRows(
      final List<CatalogRow> rows, final List<RuleDefinition> definitions) {
    final List<String> lines = new ArrayList<>();
    for (final CatalogRow row : rows) {
      for (final AnalysisLanguage language : row.languages(CatalogStatus.IMPLEMENTED)) {
        lines.add(RuleReferenceRows.implementedRow(row, language, definitions));
      }
    }
    return lines;
  }

  private static List<String> frontMatter(final String title, final String description) {
    return List.of("---", "title: " + title, "description: " + description, "---", "");
  }
}
