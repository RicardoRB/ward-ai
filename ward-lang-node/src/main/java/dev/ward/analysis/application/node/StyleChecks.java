package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import java.util.Map;

/**
 * The TypeScript and JavaScript checks that only the style (Checkstyle concepts, ESLint and Biome
 * style) rules use (issue #296).
 */
final class StyleChecks {

  private StyleChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of();
  }
}
