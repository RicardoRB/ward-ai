package dev.ward.application.gate.apicompat;

import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.MainSourceRoots;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.gate.WorkingFiles;
import java.util.Objects;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Builds the native {@code api-compat} gate. */
public final class ApiCompatGates {
  /** The builder of a run without the dependencies: only a command-based check can be built. */
  public static final ApiCompatGates UNAVAILABLE = new ApiCompatGates((ApiCompatPorts) null);

  private final ApiCompatPorts ports;

  private ApiCompatGates(final ApiCompatPorts ports) {
    this.ports = ports;
  }

  /** Creates the builder from the ports the gate uses. */
  @Inject
  public ApiCompatGates(
      final RevisionFiles revisions,
      final WorkingFiles working,
      final MainSourceRoots layout,
      final ApiSurfaces surfaces) {
    this.ports = new ApiCompatPorts(revisions, working, layout, surfaces);
  }

  /**
   * Creates the gate of {@code configuration}: the native comparison when it has no command, else
   * {@code legacy}, the gate that runs its command.
   */
  public QualityGate create(
      final GateConfiguration configuration, final Supplier<QualityGate> legacy) {
    if (!configuration.command().isEmpty()) {
      return legacy.get();
    }
    if (ports == null) {
      throw new WardException("api-compat gate dependencies are unavailable.");
    }
    return new ApiCompatGate(
        configuration.name(),
        Objects.requireNonNullElse(configuration.apiCompat(), ApiCompatOptions.DEFAULTS),
        ports);
  }
}
