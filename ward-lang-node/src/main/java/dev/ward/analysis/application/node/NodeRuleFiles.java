package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The TypeScript and JavaScript rule files packaged in this module. Add a new rule file here, in
 * alphabetical order; no other module lists it.
 */
public final class NodeRuleFiles implements RuleFileProvider {

  private static final List<String> FILES =
      List.of("dev/ward/analysis/lang/node/rules.toml", "rules/duplication-node.toml");

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
