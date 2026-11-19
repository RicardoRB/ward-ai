package dev.ward.infrastructure.project.rust;

import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectNameReader;
import java.util.Optional;
import javax.inject.Inject;

/** Reads {@code package.name} of the root {@code Cargo.toml}; a virtual workspace has none. */
public final class CargoProjectName implements ProjectNameReader {
  /** Creates the reader. */
  @Inject
  public CargoProjectName() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<String> read(final Project project) {
    return Optional.ofNullable(CargoWorkspaces.manifest(project.root()).getString("package.name"))
        .filter(name -> !name.isBlank());
  }
}
