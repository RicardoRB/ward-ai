package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The TypeScript and JavaScript rule files packaged in this module. Add a new rule file here, in
 * alphabetical order; no other module lists it.
 */
public final class NodeRuleFiles implements RuleFileProvider {

  private static final List<String> FILES =
      List.of(
          "rules/duplication-node.toml",
          "rules/node/angular.toml",
          "rules/node/blocks.toml",
          "rules/node/coding.toml",
          "rules/node/metrics.toml",
          "rules/node/misc.toml",
          "rules/node/naming.toml",
          "rules/node/problem.toml",
          "rules/node/regexp.toml",
          "rules/node/sizes.toml",
          "rules/node/suggestion.toml");

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
