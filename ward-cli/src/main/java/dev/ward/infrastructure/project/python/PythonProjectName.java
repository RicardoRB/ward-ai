package dev.ward.infrastructure.project.python;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectNameReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;
import org.tomlj.TomlParseResult;

/** Reads {@code project.name}, or {@code tool.poetry.name}, of the root {@code pyproject.toml}. */
public final class PythonProjectName implements ProjectNameReader {
  /** Creates the reader. */
  @Inject
  public PythonProjectName() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<String> read(final Project project) {
    final Path file = project.root().resolve(PythonProjectInspection.PYPROJECT);
    try {
      final TomlParseResult toml = PyprojectDependencies.parse(Files.readString(file));
      return Optional.ofNullable(toml.getString("project.name"))
          .or(() -> Optional.ofNullable(toml.getString("tool.poetry.name")))
          .filter(name -> !name.isBlank());
    } catch (IOException | WardException exception) {
      return Optional.empty();
    }
  }
}
