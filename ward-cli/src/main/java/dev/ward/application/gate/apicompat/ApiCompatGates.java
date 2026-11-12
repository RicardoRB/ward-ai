package dev.ward.application.gate.apicompat;

import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.MainSourceRoots;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.gate.WorkingFiles;
import java.util.Objects;
import javax.inject.Inject;

/** Builds the native {@code api-compat} gate. */
public final class ApiCompatGates {
  private final ApiCompatPorts ports;

  /** Creates the builder from the ports the gate uses. */
  @Inject
  public ApiCompatGates(
      final RevisionFiles revisions,
      final WorkingFiles working,
      final MainSourceRoots layout,
      final ApiSurfaces surfaces) {
    this.ports = new ApiCompatPorts(revisions, working, layout, surfaces);
  }

  /** Creates the gate {@code name} from its configuration. */
  public QualityGate create(final String name, final GateConfiguration configuration) {
    return new ApiCompatGate(
        name,
        Objects.requireNonNullElse(configuration.apiCompat(), ApiCompatOptions.DEFAULTS),
        ports);
  }
}
