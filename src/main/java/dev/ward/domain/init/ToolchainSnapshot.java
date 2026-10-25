package dev.ward.domain.init;

import dev.ward.domain.project.Framework;
import java.util.Optional;

/**
 * Project facts used to choose compatible tool versions.
 *
 * @param runtimeJdk the JDK feature version that runs the build, when the build tool reports one
 * @param declaredRelease the Java release the build file declares, if any
 * @param buildToolVersion the Maven or Gradle version, when it can be read
 * @param framework the detected application framework
 */
public record ToolchainSnapshot(
    Optional<Integer> runtimeJdk,
    Optional<Integer> declaredRelease,
    Optional<String> buildToolVersion,
    Framework framework) {
  private static final int MINIMUM_SUPPORTED_JAVA = 1;

  /**
   * Validates the versions; missing optional values mean unknown.
   *
   * @throws IllegalArgumentException if a Java version is not positive
   */
  public ToolchainSnapshot {
    runtimeJdk = runtimeJdk == null ? Optional.empty() : runtimeJdk;
    declaredRelease = declaredRelease == null ? Optional.empty() : declaredRelease;
    buildToolVersion = buildToolVersion == null ? Optional.empty() : buildToolVersion;
    framework = framework == null ? Framework.NONE : framework;
    if (runtimeJdk.filter(version -> version < MINIMUM_SUPPORTED_JAVA).isPresent()
        || declaredRelease.filter(version -> version < MINIMUM_SUPPORTED_JAVA).isPresent()) {
      throw new IllegalArgumentException("Java version must be positive");
    }
  }

  /** Returns the release the project targets: the declared one, else the runtime JDK. */
  public Optional<Integer> release() {
    return declaredRelease.or(() -> runtimeJdk);
  }
}
