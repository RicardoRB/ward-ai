package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import java.util.List;

/** Writes the {@code command} line of a check table in {@code ward.toml}. */
final class CommandLines {

  private CommandLines() {}

  /** Writes the command of {@code gate}, whose arguments after the executable are {@code goal}. */
  static void append(final StringBuilder content, final InitGateOption gate, final String goal) {
    final List<String> command = gate.command();
    if (SupportedQualityGates.TRACEABILITY.equals(gate.id()) || command.isEmpty()) {
      return;
    }
    content
        .append("command = \"")
        .append(ConfigurationTemplate.escape(command.getFirst()))
        .append(command.size() > 1 ? " " : "")
        .append(goal)
        .append("\"\n");
  }
}
