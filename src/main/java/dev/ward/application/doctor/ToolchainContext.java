package dev.ward.application.doctor;

import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildToolConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What toolchain checks read from the project beyond the environment.
 *
 * @param buildTools reads the runtime JDK and the versions the build file pins, when available
 * @param configuredGates the gates {@code ward.toml} enables, or empty when it cannot be read
 */
record ToolchainContext(Optional<BuildToolConfiguration> buildTools, List<String> configuredGates) {
  ToolchainContext {
    configuredGates = List.copyOf(configuredGates);
  }

  /**
   * Reads the gates the {@code ward.toml} under {@code root} enables, when {@code present}; a
   * missing reader or an unreadable file means none.
   */
  static ToolchainContext read(
      final Optional<BuildToolConfiguration> buildTools,
      final WardConfigurationReader reader,
      final Path root,
      final boolean present) {
    if (reader == null || !present) {
      return new ToolchainContext(buildTools, List.of());
    }
    try {
      return new ToolchainContext(
          buildTools,
          List.copyOf(
              reader.read(root.resolve(WardConfiguration.FILE_NAME)).enabledGates().keySet()));
    } catch (WardException unreadable) {
      return new ToolchainContext(buildTools, List.of());
    }
  }
}
