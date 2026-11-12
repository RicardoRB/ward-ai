package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One Ward rule concept: its status in each language and the rules of the replaced tools it stands
 * for. The id in a language is {@code ward:<language>/<name>} unless {@code ids} says otherwise,
 * which keeps ids that predate the catalog.
 *
 * @param name the concept name, lower-case words joined by hyphens
 * @param category the group of the concept; empty for a row that an implemented rule describes,
 *     since the rule definition owns the category then
 * @param presets the presets that select the concept, when known; empty for a row that an
 *     implemented rule describes or that no step has classified yet
 * @param statuses the status per language; a language that is absent does not apply
 * @param ids the id per language where it differs from the default
 * @param counterparts the upstream rule ids per tool key, see {@link CatalogTools}
 * @param source the catalog file the row was read from, for messages
 */
public record CatalogRow(
    String name,
    String category,
    Set<RulePreset> presets,
    Map<AnalysisLanguage, LanguageStatus> statuses,
    Map<AnalysisLanguage, String> ids,
    Map<String, List<String>> counterparts,
    String source) {

  /** The reason of a language that a row does not mention. */
  public static final String UNLISTED_REASON =
      "no rule of a tool in the roadmaps covers it in this language";

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

  /**
   * Validates the name, ids and counterpart tools, and copies every collection.
   *
   * @throws NullPointerException if a field is missing
   * @throws IllegalArgumentException if the name, an id or a tool key is not valid
   */
  public CatalogRow {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(source, "source");
    if (!NAME.matcher(Objects.requireNonNull(name, "name")).matches()) {
      throw new IllegalArgumentException("rule name '" + name + "' must be lower-case words");
    }
    presets = Set.copyOf(presets);
    statuses = Map.copyOf(statuses);
    ids = Map.copyOf(ids);
    final Map<String, List<String>> copy = new LinkedHashMap<>();
    counterparts.forEach((tool, rules) -> copy.put(requireTool(tool), List.copyOf(rules)));
    counterparts = Map.copyOf(copy);
    ids.forEach((language, id) -> requireId(name, language, id));
  }

  /** Returns the status of the row in {@code language}; a language it omits does not apply. */
  public LanguageStatus status(final AnalysisLanguage language) {
    return statuses.getOrDefault(language, LanguageStatus.notApplicable(UNLISTED_REASON));
  }

  /**
   * Tells whether the row is a breaking-change rule of the OpenAPI diff: a counterpart of oasdiff.
   * Such a rule compares two documents, so it has no rule definition, no presets and no single
   * document fixtures; {@code BreakingRulesCatalogTest} checks it instead.
   */
  public boolean breaking() {
    return counterparts.containsKey("oasdiff");
  }

  /** Returns the id the rule has, or would have, in {@code language}. */
  public String idFor(final AnalysisLanguage language) {
    return ids.getOrDefault(language, "ward:" + key(language) + "/" + name);
  }

  /** Returns the languages in which the row is {@code status}, in declaration order. */
  public List<AnalysisLanguage> languages(final CatalogStatus status) {
    final List<AnalysisLanguage> found = new ArrayList<>();
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      if (status(language).status() == status) {
        found.add(language);
      }
    }
    return found;
  }

  /** Returns the lower-case name of {@code language}, as used in rule ids. */
  public static String key(final AnalysisLanguage language) {
    return language.name().toLowerCase(Locale.ROOT);
  }

  private static void requireId(
      final String name, final AnalysisLanguage language, final String id) {
    final String prefix = "ward:" + key(language) + "/";
    if (!id.startsWith(prefix) || !NAME.matcher(id.substring(prefix.length())).matches()) {
      throw new IllegalArgumentException(
          "rule '" + name + "' has id '" + id + "', which must be " + prefix + "<name>");
    }
  }

  private static String requireTool(final String tool) {
    if (!CatalogTools.ALL.contains(tool)) {
      throw new IllegalArgumentException("'" + tool + "' is not a tool of the catalog");
    }
    return tool;
  }
}
