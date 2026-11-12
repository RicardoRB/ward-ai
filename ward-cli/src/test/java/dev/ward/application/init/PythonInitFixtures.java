package dev.ward.application.init;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.infrastructure.init.python.PythonBuildToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A minimal pip Python project, the only project for which init offers an installable gate tool.
 */
final class PythonInitFixtures {
  private PythonInitFixtures() {}

  /** Writes a pip project with a virtual environment into {@code root}. */
  static void project(final Path root) throws IOException {
    Files.createDirectories(root.resolve(".venv"));
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.py"), "x = 1\n");
    Files.writeString(
        root.resolve("pyproject.toml"), "[project]\nname = \"demo\"\nversion = \"1\"\n");
  }

  /** A build-tool configuration for that project that runs no real process. */
  static BuildToolConfiguration configurator() {
    return new PythonBuildToolConfigurator(new FakeCommandExecutor(0, "", ""));
  }
}
