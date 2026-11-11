package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleGroup;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The shipped rules use the sixteen families and no tool name as a group: a rule file that invents
 * a family, or names a group after a tool, fails the build. Subcategories are not pinned.
 */
class RuleGroupContractTest {

  private static final Set<String> FAMILIES =
      Set.of(
          "accessibility",
          "angular",
          "architecture",
          "complexity",
          "concurrency",
          "correctness",
          "dead-code",
          "docs",
          "duplication",
          "error-handling",
          "experimental",
          "naming",
          "null-safety",
          "performance",
          "security",
          "style");

  private static final Set<String> TOOLS =
      Set.of(
          "biome",
          "checkstyle",
          "detekt",
          "eslint",
          "errorprone",
          "ktlint",
          "nullaway",
          "pmd",
          "pylint",
          "ruff",
          "semgrep",
          "spotbugs",
          "swiftlint",
          "vulture");

  private final List<RuleDefinition> definitions = CatalogRuleDefinitions.shipped();

  @Test
  void everyShippedRuleIsInOneOfTheSixteenFamilies() {
    for (final RuleDefinition rule : definitions) {
      final String family = rule.category().split("/")[0];
      assertThat(FAMILIES).as("family of %s", rule.id()).contains(family);
    }
  }

  @Test
  void noGroupNameIsAToolName() {
    final List<String> groups =
        definitions.stream().map(RuleDefinition::category).distinct().toList();
    for (final String group : RuleGroup.names(groups)) {
      assertThat(TOOLS).as("group %s is a tool name", group).doesNotContain(group.split("/")[0]);
    }
  }
}
