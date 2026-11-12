package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import java.util.List;

/** Writes the {@code command} line of a generated check. */
final class GateCommandLine {

  private GateCommandLine() {}

  /** Appends the command line; a gate that runs inside Ward, or traceability, has none. */
  static void append(final StringBuilder content, final InitGateOption gate) {
    final List<String> command = gate.command();
    if (SupportedQualityGates.TRACEABILITY.equals(gate.id()) || command.isEmpty()) {
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
