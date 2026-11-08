package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import java.util.List;

/**
 * Test helper: the rule-file plan of a toolchain's own tools, without the shared rule selection.
 */
public final class RuleFilePlans {
  private RuleFilePlans() {}

  /** Returns {@code plan} without the {@code ward-rules.toml} every project gets. */
  public static List<PlannedFileChange> tools(final List<PlannedFileChange> plan) {
    return plan.stream()
        .filter(change -> !"ward-rules.toml".equals(change.file().getFileName().toString()))
        .toList();
  }
}
