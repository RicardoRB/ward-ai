package dev.ward.domain.gate;

/** Receives what a running gate is doing, so a console can show it while the gate works. */
@FunctionalInterface
public interface GateProgress {
  /** Ignores every step. */
  GateProgress NONE =
      step -> {
        // Optional callback.
      };

  /** Called when the gate starts {@code step}, a short lower-case phrase without source text. */
  void step(String step);
}
