package dev.ward.application.init;

import java.util.List;

/**
 * The quality checks recorded in an existing {@code ward.toml}, used to pre-fill the wizard when
 * reconfiguring. The file no longer records a preset, so init asks for one again.
 */
public record PreviousChoices(List<String> gates) {
  /** Copies the gates. */
  public PreviousChoices {
    gates = List.copyOf(gates);
  }
}
