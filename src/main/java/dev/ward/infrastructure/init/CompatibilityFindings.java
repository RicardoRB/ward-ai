package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.init.CompatibilityPlan;
import dev.ward.domain.init.CompatibilityProjectSnapshot;
import dev.ward.domain.init.CompatibilityResolver;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.ManagedPluginCandidate;
import dev.ward.domain.project.JavaSupport;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Resolves compatible catalog versions for the tools a selection needs, from one catalog. */
public final class CompatibilityFindings {
  private final CompatibilityInspection inspection;
  private final Map<String, List<ManagedPluginCandidate>> catalog;
  private final CompatibilityResolver resolver = new CompatibilityResolver();

  /** Creates the findings that read {@code inspection} and resolve against {@code catalog}. */
  public CompatibilityFindings(
      final CompatibilityInspection inspection,
      final Map<String, List<ManagedPluginCandidate>> catalog) {
    this.inspection = inspection;
    this.catalog = Map.copyOf(catalog);
  }

  /**
   * Returns one finding per catalog tool among {@code tools}, failing when any cannot be checked
   * safely. Tools with no version for the runtime JDK are returned as {@code UNAVAILABLE_ON_JDK}.
   */
  public Map<String, CompatibilityFinding> resolve(
      final Project project, final List<String> tools) {
    final CompatibilityPlan plan = plan(project, tools);
    if (plan.hasUnresolvableFindings()) {
      throw new WardException(
          plan.findings().stream()
              .filter(
                  finding ->
                      finding.outcome() == CompatibilityOutcome.UNKNOWN
                          || finding.outcome() == CompatibilityOutcome.UNRESOLVABLE
                          || finding.outcome() == CompatibilityOutcome.INCOMPATIBLE)
              .map(CompatibilityFinding::message)
              .distinct()
              .collect(Collectors.joining(" ")));
    }
    return plan.findings().stream()
        .collect(Collectors.toMap(CompatibilityFinding::artifactId, finding -> finding));
  }

  /**
   * Returns the gates of {@code toolsByGate} with a tool that has no version for the runtime JDK. A
   * toolchain that cannot be checked omits nothing here; resolving reports it.
   */
  public List<GateOmission> omissions(
      final Project project, final Map<String, List<String>> toolsByGate) {
    if (toolsByGate.isEmpty()) {
      return List.of();
    }
    final Map<String, CompatibilityFinding> findings =
        plan(project, toolsByGate.values().stream().flatMap(List::stream).distinct().toList())
            .findings()
            .stream()
            .collect(Collectors.toMap(CompatibilityFinding::artifactId, finding -> finding));
    return toolsByGate.entrySet().stream()
        .flatMap(
            entry ->
                entry.getValue().stream()
                    .map(findings::get)
                    .filter(Objects::nonNull)
                    .filter(finding -> finding.outcome() == CompatibilityOutcome.UNAVAILABLE_ON_JDK)
                    .findFirst()
                    .map(finding -> new GateOmission(entry.getKey(), finding.message()))
                    .stream())
        .toList();
  }

  /**
   * Returns the gates of {@code toolsByGate} whose pinned catalog version does not run on the
   * runtime JDK. Pins outside the catalog, and an unknown JDK, report nothing.
   */
  public List<GateOmission> unsupportedPins(
      final Project project, final Map<String, List<String>> toolsByGate) {
    final CompatibilityProjectSnapshot snapshot = inspection.inspect(project);
    final Optional<Integer> jdk = snapshot.toolchain().runtimeJdk();
    if (jdk.isEmpty()) {
      return List.of();
    }
    return toolsByGate.entrySet().stream()
        .flatMap(
            entry ->
                entry.getValue().stream()
                    .flatMap(tool -> pinnedRow(snapshot, tool).stream())
                    .filter(row -> !row.runsOn(JavaSupport.selectionJdk(jdk.get())))
                    .findFirst()
                    .map(
                        row ->
                            new GateOmission(
                                entry.getKey(),
                                row.tool()
                                    + " "
                                    + row.version()
                                    + " runs on JDK "
                                    + row.minimumJdk()
                                    + " to "
                                    + row.maximumJdk()
                                    + "; this project builds on JDK "
                                    + jdk.get()))
                    .stream())
        .toList();
  }

  private Optional<ManagedPluginCandidate> pinnedRow(
      final CompatibilityProjectSnapshot snapshot, final String tool) {
    return snapshot
        .pluginVersion(tool)
        .flatMap(
            version ->
                catalog.getOrDefault(tool, List.of()).stream()
                    .filter(row -> row.version().equals(version))
                    .findFirst());
  }

  /** Returns the JDK that runs the project's build, when the build tool reports it. */
  public Optional<Integer> runtimeJdk(final Project project) {
    return inspection.inspect(project).toolchain().runtimeJdk();
  }

  private CompatibilityPlan plan(final Project project, final List<String> tools) {
    return resolver.resolve(
        inspection.inspect(project), catalog, tools.stream().filter(catalog::containsKey).toList());
  }
}
