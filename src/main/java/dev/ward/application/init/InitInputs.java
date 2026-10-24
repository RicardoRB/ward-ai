package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.PackageManifests;

/**
 * What a toolchain support reads to offer gates.
 *
 * @param environment resolves which executables are on the path
 * @param manifests reads a Node project's {@code package.json}
 */
record InitInputs(EnvironmentInspection environment, PackageManifests manifests) {
  /** Inputs for a catalog that reads no {@code package.json}. */
  static InitInputs withoutManifests(final EnvironmentInspection environment) {
    return new InitInputs(environment, root -> PackageManifest.EMPTY);
  }
}
