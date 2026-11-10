package dev.ward.application.gate;

import dev.ward.application.format.FormatService;
import dev.ward.domain.gate.QualityGate;
import java.util.List;
import javax.inject.Inject;

/** Builds the {@code ward-format} gate over the formatting use case. */
public final class FormatGates {
  private final FormatService service;

  /** Creates the builder over the formatting use case. */
  @Inject
  public FormatGates(final FormatService service) {
    this.service = service;
  }

  /**
   * Builds the gate, which checks only the root-relative paths in {@code focus}, or every file when
   * it is empty.
   */
  public QualityGate create(final String name, final List<String> focus) {
    return new FormatGate(name, service, focus);
  }
}
