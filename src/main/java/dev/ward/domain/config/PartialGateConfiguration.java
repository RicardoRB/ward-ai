package dev.ward.domain.config;

import java.util.List;
import java.util.Objects;

/**
 * Configuration describing how a quality gate can be scoped to edited files. A non-empty {@code
 * command} replaces the gate's command as the base of a narrowed check.
 */
public record PartialGateConfiguration(
    List<String> inputs,
    List<String> config,
    List<String> args,
    String item,
    String separator,
    List<String> command) {

  /** Copies the lists. */
  public PartialGateConfiguration {
    inputs = List.copyOf(inputs);
    config = List.copyOf(config);
    args = List.copyOf(args);
    item = Objects.requireNonNullElse(item, "{path}");
    command = List.copyOf(command);
  }

  /** Creates a configuration whose narrowed check runs the gate's own command. */
  public PartialGateConfiguration(
      final List<String> inputs,
      final List<String> config,
      final List<String> args,
      final String item,
      final String separator) {
    this(inputs, config, args, item, separator, List.of());
  }
}
