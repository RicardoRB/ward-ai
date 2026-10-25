package dev.ward.application.init;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.project.PackageManifest;
import java.util.Optional;

/** Init catalogs that read the runtime JDK through a given build-tool port. */
public final class JdkCatalogs {
  private JdkCatalogs() {}

  /** A catalog with no Node packages, quality hooks, or existing configuration. */
  public static InitSetupCatalog reading(final BuildToolConfiguration buildTools) {
    return new InitSetupCatalog(
        new InitPorts(
            new FakeEnvironmentInspection(),
            new NodeInputs(
                directory -> PackageManifest.EMPTY,
                directory -> false,
                (directory, gate) -> Optional.empty()),
            buildTools),
        new NoQualityHookGeneration(),
        file -> {
          throw new WardException("no configuration");
        },
        "dev");
  }
}
