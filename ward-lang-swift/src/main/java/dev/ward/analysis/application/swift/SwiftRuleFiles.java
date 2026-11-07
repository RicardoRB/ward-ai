package dev.ward.analysis.application.swift;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The Swift rule files packaged in this module. Add a new rule file here, in alphabetical order; no
 * other module lists it.
 */
public final class SwiftRuleFiles implements RuleFileProvider {

  private static final List<String> FILES =
      List.of(
          "rules/duplication-swift.toml",
          "rules/swift-structural.toml",
          "rules/swift/declaration-order.toml",
          "rules/swift/idiomatic-2.toml",
          "rules/swift/idiomatic.toml",
          "rules/swift/lint.toml",
          "rules/swift/modifier-order.toml",
          "rules/swift/naming.toml",
          "rules/swift/part-3.toml",
          "rules/swift/performance.toml");

  /** Creates the provider. */
  public SwiftRuleFiles() {}

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
