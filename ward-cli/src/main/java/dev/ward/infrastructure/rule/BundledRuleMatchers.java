package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.infrastructure.node.NodeRuleMatcher;

/**
 * The matcher for every bundled rule. A TypeScript or JavaScript rule goes to the structural
 * matcher; every other rule goes to the matcher of its body format. The gate and the rule loader
 * both use it, so a rule the loader accepts is a rule the gate can run.
 */
public final class BundledRuleMatchers {

  private BundledRuleMatchers() {}

  /** Creates the matcher for the bundled rules. */
  public static RuleMatcher create() {
    return new LayerRuleMatcher(
        new LanguageRuleMatcher(new NodeRuleMatcher(), new CompositeRuleMatcher()));
  }
}
