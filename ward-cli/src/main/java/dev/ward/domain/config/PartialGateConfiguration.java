package dev.ward.domain.config;

import java.util.List;
import java.util.Objects;

/**
 * Configuration describing how a check can be scoped to edited files. {@code match} names the files
 * the check analyzes, {@code full} the files whose edit needs the whole check. A non-empty {@code
 * command} replaces the check's command as the base of a narrowed check.
 */
public record PartialGateConfiguration(
    List<String> match,
    List<String> full,
    List<String> args,
    String item,
    String separator,
    List<String> command) {

  /** Copies the lists. */
  public PartialGateConfiguration {
    match = List.copyOf(match);
    full = List.copyOf(full);
    args = List.copyOf(args);
    item = Objects.requireNonNullElse(item, "{path}");
    command = List.copyOf(command);
  }

  /** Creates a configuration whose narrowed check runs the gate's own command. */
  public PartialGateConfiguration(
      final List<String> match,
      final List<String> full,
      final List<String> args,
      final String item,
      final String separator) {
    this(match, full, args, item, separator, List.of());
  }
}
