package dev.ward.infrastructure.project.node;

import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectNameReader;
import java.util.Optional;
import javax.inject.Inject;

/** Reads the {@code name} of the root {@code package.json}. */
public final class NodeProjectName implements ProjectNameReader {
  /** Creates the reader. */
  @Inject
  public NodeProjectName() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<String> read(final Project project) {
    return NodeProjectInspection.manifest(project.root()).get("name") instanceof String name
            && !name.isBlank()
        ? Optional.of(name)
        : Optional.empty();
  }
}
