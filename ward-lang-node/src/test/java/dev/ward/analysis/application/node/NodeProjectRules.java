package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.check.ProjectCheck;
import java.util.Map;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/**
 * Tells the Node rules whose check reads the whole project (manifests, other files) from those that
 * read one file. Project checks run in ward-cli's RuleCatalogFixturesTest with a fixture project,
 * and their unit tests in this module cover them.
 */
final class NodeProjectRules {

  private NodeProjectRules() {}

  static boolean isProjectRule(
      final RuleDefinition rule, final Map<String, CheckFactory> factories) {
    final TomlParseResult toml = Toml.parse(rule.body());
    return factories.get(toml.getString("check")).create(toml) instanceof ProjectCheck;
  }
}
