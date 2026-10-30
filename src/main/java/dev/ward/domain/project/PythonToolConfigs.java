package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.Optional;

/** Finds the configs a Python project keeps for the tools of its gates. */
@FunctionalInterface
public interface PythonToolConfigs {
  /**
   * Returns the config of {@code tool} in {@code root} that Ward did not write, or empty when there
   * is none. The tool is {@code ruff}, {@code mypy} or {@code coverage}; a config kept in {@code
   * pyproject.toml} returns that file.
   */
  Optional<Path> userConfig(Path root, String tool);
}
