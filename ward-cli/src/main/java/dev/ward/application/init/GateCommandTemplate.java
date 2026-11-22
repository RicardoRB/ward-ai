package dev.ward.application.init;

import dev.ward.domain.config.CommandTypeInference;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import java.util.List;

/**
 * Writes the {@code uses} and {@code command} keys of a gate in the generated {@code ward.toml}.
 */
final class GateCommandTemplate {
  private GateCommandTemplate() {}

  /**
   * Whether the check needs {@code uses}: its command does not imply its type, or the type is one
   * that must never depend on command shape.
   */
  static boolean writesUses(final InitGateOption gate) {
    return SupportedQualityGates.SECRETS.equals(gate.id())
        || gate.command().isEmpty()
        || !CommandTypeInference.infer(gate.command()).equals(gate.id());
  }

  /** Single-quotes an argument that holds whitespace, which the tokenizer reads back as one. */
  private static String quoted(final String argument) {
    final String escaped = ConfigurationTemplate.escape(argument);
    return argument.chars().anyMatch(Character::isWhitespace) ? "'" + escaped + "'" : escaped;
  }

  /** Writes the command, which a gate that runs inside Ward has none of. */
  static void append(final StringBuilder content, final InitGateOption gate) {
    final List<String> command = gate.command();
    if (command.isEmpty() || SupportedQualityGates.SECRETS.equals(gate.id())) {
      return;
    }
    final String goal =
        command.stream()
            .skip(1)
            .map(GateCommandTemplate::quoted)
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
