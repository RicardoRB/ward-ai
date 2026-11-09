package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import java.util.Map;

/** The TypeScript and JavaScript checks that only the Biome lint rules use (issue #329). */
final class BiomeChecks {

  private BiomeChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of();
  }
}
