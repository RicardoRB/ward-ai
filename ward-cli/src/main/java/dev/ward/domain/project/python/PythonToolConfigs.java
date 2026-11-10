package dev.ward.domain.project.python;

import java.nio.file.Path;
import java.util.Optional;

/** Finds the configs a Python project keeps for the tools of its gates. */
@FunctionalInterface
public interface PythonToolConfigs {
  /**
   * Returns the config of {@code tool} in {@code root} that Ward did not write, or empty when there
   * is none. The tool is {@code mypy}, {@code coverage} or {@code pytest}; a config kept in {@code
   * pyproject.toml} returns that file.
   */
  Optional<Path> userConfig(Path root, String tool);

  /** Returns the Django settings module the project names, or empty when it names none. */
  default Optional<String> djangoSettings(final Path root) {
    return Optional.empty();
  }
}
