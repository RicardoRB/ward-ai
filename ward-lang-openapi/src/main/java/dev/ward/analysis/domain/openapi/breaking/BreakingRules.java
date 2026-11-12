package dev.ward.analysis.domain.openapi.breaking;

import java.util.ArrayList;
import java.util.List;

/**
 * The breaking-change rules of the OpenAPI diff: one rule per error-level check of the pinned
 * oasdiff list that Ward implements, grouped in families by what they compare.
 */
public final class BreakingRules {

  private BreakingRules() {}

  /** Returns every rule, in the order of its family. */
  public static List<BreakingRule> all() {
    final List<BreakingRule> rules = new ArrayList<>();
    rules.addAll(RemovalRules.rules());
    rules.addAll(SunsetRules.rules());
    rules.addAll(StabilityRules.rules());
    rules.addAll(WebhookRules.rules());
    rules.addAll(RequestBodyRules.rules());
    rules.addAll(SchemaRules.rules());
    rules.addAll(LifecycleRemovals.rules());
    return List.copyOf(rules);
  }

  /** Runs every rule on {@code pair} and returns the findings, rule after rule. */
  public static List<BreakingFinding> run(final DocumentPair pair) {
    final List<BreakingFinding> findings = new ArrayList<>();
    for (final BreakingRule rule : all()) {
      findings.addAll(rule.check(pair));
    }
    return findings;
  }
}
