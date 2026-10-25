package dev.ward.infrastructure.init;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks the packages init adds against the project's Node major version, and {@code
 * typescript-eslint} against its TypeScript version. A Node or TypeScript version cannot be fixed
 * by choosing another package version, so findings have no approval path.
 *
 * @param findings the {@code INCOMPATIBLE} findings, one per package
 * @param notes plan notes, such as a minimum minor Node version a range needs
 */
record NodeCompatibility(List<CompatibilityFinding> findings, List<String> notes) {
  private static final Logger LOGGER = Logger.getLogger(NodeCompatibility.class.getName());

  NodeCompatibility {
    findings = List.copyOf(findings);
    notes = List.copyOf(notes);
  }

  /**
   * Checks {@code added}, the packages init would add to {@code project}, and a declared Biome or
   * ESLint when {@code biome} or {@code eslint} is selected.
   */
  static NodeCompatibility check(
      final Project project,
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added,
      final boolean biome,
      final boolean eslint) {
    final int node = Integer.parseInt(project.languageVersion());
    final List<CompatibilityFinding> findings = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    for (final NodePackageCatalog.Package tool : added) {
      final Optional<String> minimum = NodeVersionRange.nodeMinimum(tool.node(), node);
      if (minimum.isEmpty()) {
        findings.add(
            finding(
                tool,
                "Node " + node,
                tool.name()
                    + " "
                    + tool.version()
                    + " supports Node "
                    + tool.node()
                    + "; the project uses Node "
                    + node
                    + "."));
      } else if (minimum.get().startsWith(node + ".") && !minimum.get().endsWith(".0.0")) {
        notes.add(tool.name() + " needs Node " + minimum.get() + " or later.");
      }
      typescript(tool, manifest, added).ifPresent(findings::add);
    }
    if (biome) {
      declaredBiome(manifest).ifPresent(findings::add);
    }
    if (eslint) {
      declaredEslint(manifest).ifPresent(findings::add);
    }
    findings.forEach(
        finding ->
            LOGGER.log(
                Level.INFO,
                () ->
                    "event=node-compatibility outcome="
                        + finding.outcome()
                        + " tool="
                        + finding.artifactId()));
    return new NodeCompatibility(findings, notes);
  }

  private static Optional<CompatibilityFinding> typescript(
      final NodePackageCatalog.Package tool,
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added) {
    if (tool.typescript() == null) {
      return Optional.empty();
    }
    final Optional<String> version =
        manifest.declares(NodePackageCatalog.TYPESCRIPT)
            ? NodeVersionRange.lowerBound(
                manifest.dependencies().get(NodePackageCatalog.TYPESCRIPT))
            : added.stream()
                .filter(candidate -> NodePackageCatalog.TYPESCRIPT.equals(candidate.name()))
                .map(NodePackageCatalog.Package::version)
                .findFirst();
    return version
        .filter(declared -> !NodeVersionRange.matches(tool.typescript(), declared))
        .map(
            declared ->
                finding(
                    tool,
                    "TypeScript " + declared,
                    tool.name()
                        + " "
                        + tool.version()
                        + " supports TypeScript "
                        + tool.typescript()
                        + "; the project uses TypeScript "
                        + declared
                        + "."));
  }

  /** A declared Biome must start inside the range Ward's checkstyle reading supports. */
  private static Optional<CompatibilityFinding> declaredBiome(final PackageManifest manifest) {
    final String declared = manifest.dependencies().get(NodePackageCatalog.BIOME_PACKAGE);
    if (declared == null) {
      return Optional.empty();
    }
    return NodeVersionRange.lowerBound(declared)
        .filter(version -> !NodeVersionRange.matches(NodePackageCatalog.BIOME_SUPPORTED, version))
        .map(
            version ->
                new CompatibilityFinding(
                    NodePackageCatalog.BIOME_PACKAGE,
                    "Biome " + version,
                    NodePackageCatalog.BIOME_VERSION,
                    CompatibilityOutcome.INCOMPATIBLE,
                    NodePackageCatalog.BIOME_PACKAGE
                        + " "
                        + declared
                        + " is outside the supported range "
                        + NodePackageCatalog.BIOME_SUPPORTED
                        + "."));
  }

  /** A declared ESLint must start inside the range the generated config supports. */
  private static Optional<CompatibilityFinding> declaredEslint(final PackageManifest manifest) {
    final String declared = manifest.dependencies().get(NodePackageCatalog.ESLINT);
    if (declared == null) {
      return Optional.empty();
    }
    return NodeVersionRange.lowerBound(declared)
        .filter(version -> !reachesSupportedEslint(declared, version))
        .map(
            version ->
                new CompatibilityFinding(
                    NodePackageCatalog.ESLINT,
                    "ESLint " + version,
                    NodePackageCatalog.ESLINT_VERSION,
                    CompatibilityOutcome.INCOMPATIBLE,
                    NodePackageCatalog.ESLINT
                        + " "
                        + declared
                        + " is outside the supported range "
                        + NodePackageCatalog.ESLINT_SUPPORTED
                        + "."));
  }

  /**
   * Returns whether {@code declared} can resolve inside the supported ESLint range. A caret range
   * such as {@code ^9} resolves to the newest release of its major, so it reaches the range when
   * that major has a supported release; the top of the major stands for all of it.
   */
  private static boolean reachesSupportedEslint(final String declared, final String lowerBound) {
    if (NodeVersionRange.matches(NodePackageCatalog.ESLINT_SUPPORTED, lowerBound)) {
      return true;
    }
    final String major = lowerBound.substring(0, lowerBound.indexOf('.'));
    return declared.startsWith("^")
        && NodeVersionRange.matches(NodePackageCatalog.ESLINT_SUPPORTED, major + ".999.999");
  }

  private static CompatibilityFinding finding(
      final NodePackageCatalog.Package tool, final String current, final String message) {
    return new CompatibilityFinding(
        tool.name(), current, tool.version(), CompatibilityOutcome.INCOMPATIBLE, message);
  }

  /** Returns whether any finding blocks init. */
  boolean incompatible() {
    return !findings.isEmpty();
  }

  /** Returns the findings' messages, joined for an error. */
  String message() {
    return String.join(" ", findings.stream().map(CompatibilityFinding::message).toList());
  }
}
