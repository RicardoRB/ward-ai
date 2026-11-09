package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import java.util.Map;

/** The TypeScript and JavaScript checks that only the dead-code (knip) rules use (issue #331). */
final class DeadCodeChecks {

  private DeadCodeChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of();
  }
}
