package dev.ward.application.init;

import dev.ward.domain.config.CommandTypeInference;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import java.util.List;
import java.util.Set;

/**
 * Writes the {@code uses} and {@code command} keys of a gate in the generated {@code ward.toml}.
 */
final class GateCommandTemplate {

  /** Types written with {@code uses} even when their command would infer them. */
  private static final Set<String> CRITICAL_USES = Set.of("openspec", "openspec-archived");

  private GateCommandTemplate() {}

  /**
   * Whether the check needs {@code uses}: its command does not imply its type, or the type is one
   * that must never depend on command shape.
   */
  static boolean writesUses(final InitGateOption gate) {
    return CRITICAL_USES.contains(gate.id())
        || SupportedQualityGates.SECRETS.equals(gate.id())
        || !CommandTypeInference.infer(gate.command()).equals(gate.id());
  }

  /** Writes the command, which a gate that runs inside Ward has none of. */
  static void append(final StringBuilder content, final InitGateOption gate) {
    final List<String> command = gate.command();
    if (command.isEmpty()
        || SupportedQualityGates.TRACEABILITY.equals(gate.id())
        || SupportedQualityGates.SECRETS.equals(gate.id())) {
      return;
    }
    final String goal =
        command.stream()
            .skip(1)
            .map(ConfigurationTemplate::escape)
            .reduce((left, right) -> left + " " + right)
            .orElse("");
    content
        .append("command = \"")
        .append(ConfigurationTemplate.escape(command.getFirst()))
        .append(command.size() > 1 ? " " : "")
        .append(goal)
        .append("\"\n");
  }
}
