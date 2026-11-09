package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import java.util.Map;

/** The TypeScript and JavaScript checks that only the ESLint core rules use (issue #326). */
final class EslintChecks {

  private EslintChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of();
  }
}
