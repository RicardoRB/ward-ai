package dev.ward.analysis.application.jvm;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NullnessCheck;
import java.util.HashMap;
import java.util.Map;

/** The checks of the NullAway-style nullness rules of Java, one per rule name. */
final class NullnessChecks {

  private NullnessChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> factories = new HashMap<>();
    for (final String name : NullnessCheck.names()) {
      factories.put(name, toml -> NullnessCheck.of(name, TomlValues.required(toml, "message")));
    }
    return Map.copyOf(factories);
  }
}
