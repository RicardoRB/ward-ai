package dev.ward.infrastructure.init;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.project.PackageManifest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks the Angular release, TypeScript, angular-eslint, and Node versions as one compatibility
 * set.
 */
final class NodeAngularCompatibility {
  private NodeAngularCompatibility() {}

  /** Returns Angular compatibility findings for the project's declared or added packages. */
  static List<CompatibilityFinding> check(
      final int node,
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added) {
    if (!manifest.declares(NodePackageCatalog.ANGULAR_CORE)) {
      return List.of();
    }
    final Optional<String> major = NodePackageCatalog.angularMajor(manifest);
    return major
        .flatMap(AngularReleases::forMajor)
        .map(release -> supportedRelease(node, manifest, added, release))
        .orElseGet(() -> List.of(unsupportedMajor(manifest, major)));
  }

  private static List<CompatibilityFinding> supportedRelease(
      final int node,
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added,
      final AngularRelease release) {
    final List<CompatibilityFinding> findings = new ArrayList<>();
    typescript(manifest, added, release).ifPresent(findings::add);
    angularEslint(manifest, added, release).ifPresent(findings::add);
    node(node, release).ifPresent(findings::add);
    return List.copyOf(findings);
  }

  private static CompatibilityFinding unsupportedMajor(
      final PackageManifest manifest, final Optional<String> major) {
    final String declared = manifest.dependencies().get(NodePackageCatalog.ANGULAR_CORE);
    final String supported = String.join(", ", AngularReleases.majors());
    final String current = major.map(value -> "Angular " + value).orElse("Angular " + declared);
    return new CompatibilityFinding(
        NodePackageCatalog.ANGULAR_CORE,
        current,
        supported,
        CompatibilityOutcome.INCOMPATIBLE,
        NodePackageCatalog.ANGULAR_CORE
            + " "
            + declared
            + " is outside the supported Angular majors "
            + supported
            + ".");
  }

  private static Optional<CompatibilityFinding> typescript(
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added,
      final AngularRelease release) {
    return packageVersion(NodePackageCatalog.TYPESCRIPT, manifest, added)
        .flatMap(NodeVersionRange::lowerBound)
        .filter(version -> !NodeVersionRange.matches(release.typescript(), version))
        .map(
            version ->
                new CompatibilityFinding(
                    NodePackageCatalog.TYPESCRIPT,
                    "TypeScript " + version,
                    release.typescript(),
                    CompatibilityOutcome.INCOMPATIBLE,
                    "Angular "
                        + release.major()
                        + " supports TypeScript "
                        + release.typescript()
                        + "; the project uses TypeScript "
                        + version
                        + "."));
  }

  private static Optional<CompatibilityFinding> angularEslint(
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added,
      final AngularRelease release) {
    return packageVersion(NodePackageCatalog.ANGULAR_ESLINT, manifest, added)
        .filter(version -> !matchesMajor(version, release.major()))
        .map(
            version ->
                new CompatibilityFinding(
                    NodePackageCatalog.ANGULAR_ESLINT,
                    "angular-eslint " + NodeVersionRange.lowerBound(version).orElse(version),
                    release.angularEslint(),
                    CompatibilityOutcome.INCOMPATIBLE,
                    "angular-eslint "
                        + version
                        + " does not match Angular "
                        + release.major()
                        + "; use angular-eslint "
                        + release.angularEslint()
                        + "."));
  }

  private static boolean matchesMajor(final String version, final String major) {
    return NodeVersionRange.lowerBound(version)
        .map(lowerBound -> lowerBound.substring(0, lowerBound.indexOf('.')))
        .filter(major::equals)
        .isPresent();
  }

  private static Optional<CompatibilityFinding> node(final int node, final AngularRelease release) {
    if (NodeVersionRange.nodeMinimum(release.node(), node).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        new CompatibilityFinding(
            NodePackageCatalog.ANGULAR_CORE,
            "Node " + node,
            release.node(),
            CompatibilityOutcome.INCOMPATIBLE,
            "Angular "
                + release.major()
                + " supports Node "
                + release.node()
                + "; the project uses Node "
                + node
                + "."));
  }

  private static Optional<String> packageVersion(
      final String name,
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added) {
    if (manifest.declares(name)) {
      return Optional.of(manifest.dependencies().get(name));
    }
    return added.stream()
        .filter(tool -> name.equals(tool.name()))
        .map(NodePackageCatalog.Package::version)
        .findFirst();
  }
}
