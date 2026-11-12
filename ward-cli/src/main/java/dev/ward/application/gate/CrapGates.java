package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGate;

/** Builds the {@code crap} gate from its check and the optional gate dependencies. */
final class CrapGates {

  private CrapGates() {}

  /**
   * Builds the gate for {@code check}.
   *
   * @throws WardException if the analysis or report ports are unavailable
   */
  static QualityGate create(final GateConfiguration check, final GateExtensions extensions) {
    if (extensions.analysis() == null || extensions.crap() == null) {
      throw new WardException("CRAP gate dependencies are unavailable.");
    }
    return new CrapGate(check.name(), check.crap(), extensions.analysis(), extensions.crap());
  }
}
