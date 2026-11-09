package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import java.util.Map;

/** The TypeScript and JavaScript checks that only the Semgrep rules use (issue #446). */
final class SemgrepChecks {

  private SemgrepChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of();
  }
}
