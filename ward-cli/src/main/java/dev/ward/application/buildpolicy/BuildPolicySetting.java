package dev.ward.application.buildpolicy;

import dev.ward.domain.buildpolicy.BuildRule;
import java.util.Map;

/**
 * A selected rule as configured.
 *
 * @param rule the rule
 * @param warning whether its findings are warnings that do not fail the check
 * @param options its options by name
 */
public record BuildPolicySetting(BuildRule rule, boolean warning, Map<String, String> options) {
  /** Copies the options. */
  public BuildPolicySetting {
    options = Map.copyOf(options);
  }
}
