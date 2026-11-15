package dev.ward.analysis.application.android;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The Android rule files packaged in this module. Add a new rule file here, in alphabetical order;
 * no other module lists it.
 */
public final class AndroidRuleFiles implements RuleFileProvider {

  private static final List<String> FILES =
      List.of("rules/android/manifest.toml", "rules/android/resources.toml");

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
