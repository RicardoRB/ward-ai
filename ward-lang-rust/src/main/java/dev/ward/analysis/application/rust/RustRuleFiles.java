package dev.ward.analysis.application.rust;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The Rust rule files packaged in this module. Add a new rule file here, in alphabetical order; no
 * other module lists it. There are none yet: the syntax, scopes and control flow come first.
 */
public final class RustRuleFiles implements RuleFileProvider {
  private static final List<String> FILES = List.of();

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
