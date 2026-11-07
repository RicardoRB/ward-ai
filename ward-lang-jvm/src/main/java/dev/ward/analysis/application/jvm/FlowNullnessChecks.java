package dev.ward.analysis.application.jvm;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.FlowNullnessCheck;
import java.util.HashMap;
import java.util.Map;

/** The checks of the SpotBugs null-pointer and type-qualifier rules that read the data flow. */
final class FlowNullnessChecks {

  private FlowNullnessChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> factories = new HashMap<>();
    for (final String name : FlowNullnessCheck.names()) {
      factories.put(name, toml -> FlowNullnessCheck.of(name, TomlValues.required(toml, "message")));
    }
    return Map.copyOf(factories);
  }
}
