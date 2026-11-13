package dev.ward.domain.buildpolicy;

import java.util.List;

/** One build policy rule. */
@FunctionalInterface
public interface BuildPolicyRule {
  /** Returns the violations of this rule in {@code input}. */
  List<BuildPolicyFinding> check(BuildPolicyInput input);
}
