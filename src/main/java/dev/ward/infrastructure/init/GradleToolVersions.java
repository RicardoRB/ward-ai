package dev.ward.infrastructure.init;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.init.GradleToolCatalog;
import dev.ward.domain.project.JavaSupport;
import dev.ward.domain.project.Project;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The Gradle plugin and engine versions chosen for the selected gates of one project. */
final class GradleToolVersions {
  /** The catalog tools each gate pins, in the order init writes them. */
  private static final Map<String, List<String>> GATE_TOOLS = gateTools();

  private final Map<String, CompatibilityFinding> findings;
  private final Optional<String> warning;

  private GradleToolVersions(
      final Map<String, CompatibilityFinding> findings, final Optional<String> warning) {
    this.findings = Map.copyOf(findings);
    this.warning = warning;
  }

  /**
   * Resolves the tools of the {@code selected} gates; without {@code compatibility} each tool gets
   * its newest catalog version.
   */
  static GradleToolVersions resolve(
      final Optional<CompatibilityFindings> compatibility,
      final Project project,
      final Set<String> selected) {
    return compatibility
        .map(
            resolver ->
                new GradleToolVersions(
                    resolver.resolve(
                        project,
                        toolsByGate(selected).values().stream().flatMap(List::stream).toList()),
                    resolver.runtimeJdk(project).flatMap(JavaSupport::warning)))
        .orElseGet(() -> new GradleToolVersions(Map.of(), Optional.empty()));
  }

  /** The catalog tools each selected gate pins. */
  static Map<String, List<String>> toolsByGate(final Set<String> selected) {
    final Map<String, List<String>> tools = new LinkedHashMap<>();
    GATE_TOOLS.forEach(
        (gate, names) -> {
          if (selected.contains(gate)) {
            tools.put(gate, names);
          }
        });
    return tools;
  }

  /** The version to write for {@code tool}: the resolved one, else its newest catalog version. */
  String version(final String tool) {
    return Optional.ofNullable(findings.get(tool))
        .map(CompatibilityFinding::selectedVersion)
        .orElseGet(() -> GradleToolCatalog.entries().get(tool).getLast().version());
  }

  /** Why {@code gate} cannot run on the runtime JDK, when one of its tools has no version. */
  Optional<String> unavailable(final String gate) {
    return GATE_TOOLS.getOrDefault(gate, List.of()).stream()
        .map(findings::get)
        .filter(Objects::nonNull)
        .filter(finding -> finding.outcome() == CompatibilityOutcome.UNAVAILABLE_ON_JDK)
        .map(CompatibilityFinding::message)
        .findFirst();
  }

  /** The warning for a runtime JDK Ward does not support, if any. */
  Optional<String> warning() {
    return warning;
  }

  private static Map<String, List<String>> gateTools() {
    final Map<String, List<String>> tools = new LinkedHashMap<>();
    tools.put("format", List.of(GradleToolCatalog.SPOTLESS, GradleToolCatalog.FORMATTER));
    tools.put("checkstyle", List.of(GradleToolCatalog.CHECKSTYLE_ENGINE));
    tools.put("pmd", List.of(GradleToolCatalog.PMD_ENGINE));
    tools.put("spotbugs", List.of(GradleToolCatalog.SPOTBUGS, GradleToolCatalog.SPOTBUGS_ENGINE));
    tools.put("coverage", List.of(GradleToolCatalog.JACOCO_ENGINE));
    tools.put("archunit", List.of(GradleToolCatalog.ARCHUNIT));
    tools.put("mutation", List.of(GradleToolCatalog.PITEST, GradleToolCatalog.PITEST_ENGINE));
    tools.put("dependency-check", List.of("org.owasp.dependencycheck"));
    tools.put("sonar", List.of("org.sonarqube"));
    tools.put("license", List.of("com.github.jk1.dependency-license-report"));
    tools.put("api-compat", List.of("me.champeau.gradle.japicmp"));
    return Collections.unmodifiableMap(tools);
  }
}
