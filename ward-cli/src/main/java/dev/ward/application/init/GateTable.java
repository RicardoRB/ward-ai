package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityGatePhasePolicy;
import java.util.List;

/** Writes the head of a gate's {@code [checks.<id>]} table: profiles, {@code uses}, command. */
final class GateTable {
  private GateTable() {}

  /** Appends the table head of {@code gate}; {@code uses} says whether to write {@code uses}. */
  static void append(final StringBuilder content, final InitGateOption gate, final boolean uses) {
    content
        .append("\n[checks.")
        .append(gate.id())
        .append("]\nenabled = true\nprofiles = [")
        .append(
            String.join(
                ", ",
                QualityGatePhasePolicy.profilesFor(gate.id()).stream()
                    .map(profile -> "\"" + profile + "\"")
                    .toList()))
        .append("]\n");
    if (uses) {
      content.append("uses = \"").append(gate.id()).append("\"\n");
    }
    appendCommand(content, gate);
  }

  private static void appendCommand(final StringBuilder content, final InitGateOption gate) {
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
