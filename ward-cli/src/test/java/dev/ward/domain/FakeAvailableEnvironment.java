package dev.ward.domain;

import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.doctor.ExecutableObservation;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Reports every executable as available or not. */
public final class FakeAvailableEnvironment implements EnvironmentInspection {
  private final boolean available;

  /** Creates an environment where every executable is usable only when {@code available}. */
  public FakeAvailableEnvironment(final boolean available) {
    this.available = available;
  }

  @Override
  public EnvironmentFacts inspect(final Path projectRoot) {
    return new EnvironmentFacts(Set.of(), Map.of());
  }

  @Override
  public ExecutableObservation inspectExecutable(final Path projectRoot, final String requested) {
    return new ExecutableObservation(
        requested, Path.of(requested), available, available, available);
  }
}
