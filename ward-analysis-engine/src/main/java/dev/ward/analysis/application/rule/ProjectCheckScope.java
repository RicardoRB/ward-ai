package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Which files a whole-project check reads. A check reads the units of its own language, and the
 * other languages its frontend declares, and a check is not run again on the files another check of
 * the same name already reads.
 */
final class ProjectCheckScope {

  private ProjectCheckScope() {}

  /**
   * The languages a project check reads: the languages its rule runs on, and the other languages
   * the frontend of each of them declares its project checks read, where the frontend provides the
   * rule's capability.
   */
  static Set<AnalysisLanguage> reads(
      final PreparedRule prepared,
      final RuleDefinition definition,
      final Map<AnalysisLanguage, FrontendSupport> support) {
    final Set<AnalysisLanguage> reads = EnumSet.noneOf(AnalysisLanguage.class);
    reads.addAll(prepared.runnable());
    for (final AnalysisLanguage language : definition.languages()) {
      Optional.ofNullable(support.get(language))
          .map(FrontendSupport::projectLanguages)
          .orElse(Set.of())
          .forEach(
              extra ->
                  Optional.ofNullable(support.get(extra))
                      .filter(frontend -> frontend.capabilities().contains(definition.capability()))
                      .ifPresent(frontend -> reads.add(extra)));
    }
    return reads;
  }

  /**
   * Tells whether another project check of the same name runs over the files this one reads, so
   * this one would report the same files twice: a TypeScript check reads its JavaScript files.
   */
  static boolean coveredByAnother(
      final RuleDefinition definition,
      final List<PreparedRule> prepared,
      final Map<AnalysisLanguage, FrontendSupport> support) {
    return prepared.stream()
        .flatMap(rule -> rule.rule().stream())
        .filter(CompiledRule::isProject)
        .map(CompiledRule::definition)
        .filter(other -> !other.id().equals(definition.id()))
        .filter(other -> checkName(other).equals(checkName(definition)))
        .anyMatch(other -> readsLanguagesOf(other, definition, support));
  }

  private static boolean readsLanguagesOf(
      final RuleDefinition reader,
      final RuleDefinition read,
      final Map<AnalysisLanguage, FrontendSupport> support) {
    return reader.languages().stream()
        .anyMatch(
            language ->
                Optional.ofNullable(support.get(language))
                    .map(FrontendSupport::projectLanguages)
                    .orElse(Set.of())
                    .containsAll(read.languages()));
  }

  /** The name of a rule after its language, such as {@code knip-unresolved}. */
  private static String checkName(final RuleDefinition definition) {
    return definition.id().substring(definition.id().indexOf('/') + 1);
  }

  /** The syntax trees of the units the rule reads, by path, in path order. */
  static SortedMap<String, SyntaxNode> parsedTrees(
      final Set<AnalysisLanguage> reads,
      final List<SourceUnit> units,
      final UnitTrees trees,
      final RuleRunCollector collector) {
    final SortedMap<String, SyntaxNode> parsed = new TreeMap<>();
    for (final SourceUnit unit : units) {
      if (reads.contains(unit.language())) {
        trees.parse(unit, collector).ifPresent(root -> parsed.put(unit.path(), root));
      }
    }
    return parsed;
  }
}
