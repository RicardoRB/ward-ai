package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The secret rule file packaged in Ward: one rule per rule of the pinned Gitleaks default
 * configuration, with their regexes and allowlists as data.
 */
public final class SecretRuleFiles implements RuleFileProvider {

  private static final List<String> FILES = List.of("rules/secrets/secrets.toml");

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
