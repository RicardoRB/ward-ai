package dev.ward.domain.init;

import dev.ward.domain.project.JavaSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure, deterministic selection of compatible managed tool versions. */
public final class CompatibilityResolver {

  /**
   * Chooses, for each selected tool, the newest catalog version that runs on the project's runtime
   * JDK and build tool version. The release never exceeds the runtime JDK, so it is covered too.
   */
  public CompatibilityPlan resolve(
      final CompatibilityProjectSnapshot snapshot,
      final Map<String, List<ManagedPluginCandidate>> catalog,
      final List<String> selectedArtifacts) {
    return new CompatibilityPlan(
        selectedArtifacts.stream()
            .map(
                artifact ->
                    resolveOne(snapshot, catalog.getOrDefault(artifact, List.of()), artifact))
            .toList());
  }

  private CompatibilityFinding resolveOne(
      final CompatibilityProjectSnapshot snapshot,
      final List<ManagedPluginCandidate> candidates,
      final String tool) {
    final String current = snapshot.pluginVersion(tool).orElse(null);
    if (candidates.isEmpty()) {
      return new CompatibilityFinding(
          tool,
          current,
          null,
          CompatibilityOutcome.UNRESOLVABLE,
          "No compatibility catalog entry exists for this managed tool.");
    }
    final ToolchainSnapshot toolchain = snapshot.toolchain();
    final Optional<String> unknown = unknownToolchain(toolchain);
    if (unknown.isPresent()) {
      return new CompatibilityFinding(
          tool, current, null, CompatibilityOutcome.UNKNOWN, unknown.get());
    }
    final int jdk = JavaSupport.selectionJdk(toolchain.runtimeJdk().orElseThrow());
    final String buildTool = toolchain.buildToolVersion().orElseThrow();
    final List<ManagedPluginCandidate> compatible =
        candidates.stream()
            .filter(candidate -> candidate.runsOn(jdk))
            .filter(candidate -> compareVersions(buildTool, candidate.minimumBuildTool()) >= 0)
            .sorted((left, right) -> compareVersions(right.version(), left.version()))
            .toList();
    if (compatible.isEmpty()) {
      return new CompatibilityFinding(
          tool,
          current,
          null,
          CompatibilityOutcome.UNAVAILABLE_ON_JDK,
          unavailable(tool, candidates, jdk, buildTool));
    }
    return selected(tool, current, compatible);
  }

  /**
   * Explains why the toolchain cannot be checked, or is empty when it can. A release above the
   * runtime JDK cannot compile, so its toolchain is misconfigured rather than guessed at.
   */
  private static Optional<String> unknownToolchain(final ToolchainSnapshot toolchain) {
    if (toolchain.buildToolVersion().isEmpty()) {
      return Optional.of(
          "Build tool version could not be determined; install Maven or Gradle, or provide a"
              + " wrapper.");
    }
    if (toolchain.runtimeJdk().isEmpty()) {
      return Optional.of(
          "Runtime JDK could not be determined; the build tool did not report a Java version.");
    }
    final int jdk = toolchain.runtimeJdk().get();
    final int release = toolchain.release().orElse(jdk);
    return release > jdk
        ? Optional.of(
            "Java release "
                + release
                + " is above the runtime JDK "
                + jdk
                + "; run the build on JDK "
                + release
                + " or later.")
        : Optional.empty();
  }

  private static String unavailable(
      final String tool,
      final List<ManagedPluginCandidate> candidates,
      final int jdk,
      final String buildTool) {
    final boolean jdkCovered = candidates.stream().anyMatch(candidate -> candidate.runsOn(jdk));
    if (jdkCovered) {
      final String minimum =
          candidates.stream()
              .filter(candidate -> candidate.runsOn(jdk))
              .map(ManagedPluginCandidate::minimumBuildTool)
              .min(CompatibilityResolver::compareVersions)
              .orElseThrow();
      return tool
          + " needs build tool version "
          + minimum
          + "+ on JDK "
          + jdk
          + "; this project uses "
          + buildTool;
    }
    final int minimumJdk =
        candidates.stream().mapToInt(ManagedPluginCandidate::minimumJdk).min().orElseThrow();
    return tool + " needs JDK " + minimumJdk + "+; this project builds on JDK " + jdk;
  }

  private static CompatibilityFinding selected(
      final String tool, final String current, final List<ManagedPluginCandidate> compatible) {
    if (current != null
        && compatible.stream().anyMatch(candidate -> candidate.version().equals(current))) {
      return new CompatibilityFinding(
          tool,
          current,
          current,
          CompatibilityOutcome.COMPATIBLE,
          "Existing version is compatible with the detected toolchain.");
    }
    final String chosen = compatible.getFirst().version();
    return new CompatibilityFinding(
        tool,
        current,
        chosen,
        current == null
            ? CompatibilityOutcome.COMPATIBLE
            : CompatibilityOutcome.UPGRADE_RECOMMENDED,
        current == null
            ? "Selected the highest compatible catalog version."
            : "Existing version is incompatible; a compatible upgrade is available.");
  }

  static int compareVersions(final String left, final String right) {
    final String[] leftParts = left.split("[.-]");
    final String[] rightParts = right.split("[.-]");
    final int length = Math.max(leftParts.length, rightParts.length);
    for (int index = 0; index < length; index++) {
      final int leftPart = numericPart(leftParts, index);
      final int rightPart = numericPart(rightParts, index);
      if (leftPart != rightPart) {
        return Integer.compare(leftPart, rightPart);
      }
    }
    return 0;
  }

  private static int numericPart(final String[] parts, final int index) {
    if (index >= parts.length) {
      return 0;
    }
    final String digits = parts[index].replaceAll("[^0-9].*", "");
    return digits.isEmpty() ? 0 : Integer.parseInt(digits);
  }
}
