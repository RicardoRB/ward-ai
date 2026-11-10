package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.util.ArrayList;
import java.util.List;

/**
 * The rule definitions that the catalog describes: the rules that ship, and the example rules of
 * the generic check kinds, which only tests load from {@code rules-examples/}.
 */
final class CatalogRuleDefinitions {

  private static final List<String> EXAMPLE_FILES =
      List.of(
          "rules-examples/java.toml", "rules-examples/python.toml", "rules-examples/scopes.toml");

  private CatalogRuleDefinitions() {}

  /** Returns every definition of the shipped rules, then of the examples. */
  static List<RuleDefinition> all() {
    final List<RuleDefinition> rules = new ArrayList<>(new ClasspathRuleSource().load().rules());
    rules.addAll(new ClasspathRuleSource(EXAMPLE_FILES).load().rules());
    return List.copyOf(rules);
  }

  /** Returns the definitions of the rules that ship with Ward. */
  static List<RuleDefinition> shipped() {
    return new ClasspathRuleSource().load().rules();
  }
}
