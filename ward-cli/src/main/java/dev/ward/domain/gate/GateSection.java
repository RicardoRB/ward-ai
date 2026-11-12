package dev.ward.domain.gate;

import java.util.Objects;

/**
 * One part of a gate that checks several things, such as a rule category of the {@code ward} gate.
 *
 * @param name what the part checks, for example {@code style}
 * @param errors the error findings of the part
 * @param warnings the warning findings of the part
 */
public record GateSection(String name, int errors, int warnings) {

  /** Requires a name. */
  public GateSection {
    Objects.requireNonNull(name, "name");
  }
}
