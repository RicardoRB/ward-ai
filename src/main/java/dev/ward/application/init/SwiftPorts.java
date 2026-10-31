package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.SwiftSourceDirectories;
import dev.ward.domain.project.SwiftToolConfigs;
import dev.ward.domain.project.XcodeSchemes;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The read ports the Swift init support uses.
 *
 * @param xcode lists the Xcode workspace, schemes and platform of an Xcode project
 * @param configs finds the Swift tool configs a project keeps itself
 * @param sources lists the source directories the formatter checks
 */
public record SwiftPorts(
    XcodeSchemes xcode, SwiftToolConfigs configs, SwiftSourceDirectories sources) {
  /** Ports for a catalog that reads no Swift project; using them fails loudly. */
  public static final SwiftPorts NONE =
      new SwiftPorts(
          new XcodeSchemes() {
            @Override
            public Optional<String> workspace(final Path root) {
              throw unavailable();
            }

            @Override
            public List<String> schemes(final Path root) {
              throw unavailable();
            }

            @Override
            public boolean macOsOnly(final Path root) {
              throw unavailable();
            }
          },
          (root, fileName) -> Optional.empty(),
          root -> List.of());

  private static WardException unavailable() {
    return new WardException("Swift projects need the Xcode and source ports, which are not set.");
  }
}
