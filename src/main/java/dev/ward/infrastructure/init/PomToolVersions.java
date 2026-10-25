package dev.ward.infrastructure.init;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.project.JavaSupport;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.PomTools.Tool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** The versions compatibility resolution chose for each selected Maven tool and its engine. */
final class PomToolVersions {
  private final Map<String, CompatibilityFinding> findings;
  private final Optional<String> warning;

  private PomToolVersions(
      final Map<String, CompatibilityFinding> findings, final Optional<String> warning) {
    this.findings = Map.copyOf(findings);
    this.warning = warning;
  }

  /**
   * Resolves the plugins and engines of the {@code selected} gates; without {@code compatibility}
   * every tool keeps its default version.
   */
  static PomToolVersions resolve(
      final Optional<CompatibilityFindings> compatibility,
      final Project project,
      final Set<String> selected) {
    return compatibility
        .map(
            resolver ->
                new PomToolVersions(
                    resolver.resolve(
                        project,
                        toolsByGate(selected).values().stream().flatMap(List::stream).toList()),
                    resolver.runtimeJdk(project).flatMap(JavaSupport::warning)))
        .orElseGet(() -> new PomToolVersions(Map.of(), Optional.empty()));
  }

  /** The warning for a runtime JDK Ward does not support, if any. */
  Optional<String> warning() {
    return warning;
  }

  /** The plugin and engine each selected gate pins, in pom order. */
  static Map<String, List<String>> toolsByGate(final Set<String> selected) {
    final Map<String, List<String>> tools = new LinkedHashMap<>();
    PomTools.TOOLS.stream()
        .filter(tool -> selected.contains(tool.gate()))
        .forEach(tool -> tools.put(tool.gate(), names(tool).toList()));
    return tools;
  }

  /** The finding for {@code tool}'s plugin, or {@code null} when it was not resolved. */
  CompatibilityFinding plugin(final Tool tool) {
    return findings.get(tool.artifact());
  }

  /** The version resolved for {@code tool}'s engine, or {@code null} for the default. */
  String engine(final Tool tool) {
    return Optional.ofNullable(PomTools.ENGINES.get(tool.artifact()))
        .map(engine -> findings.get(engine.name()))
        .map(CompatibilityFinding::selectedVersion)
        .orElse(null);
  }

  /** Why {@code tool} cannot run on the runtime JDK, when its plugin or engine has no version. */
  Optional<String> unavailable(final Tool tool) {
    return names(tool)
        .map(findings::get)
        .filter(Objects::nonNull)
        .filter(finding -> finding.outcome() == CompatibilityOutcome.UNAVAILABLE_ON_JDK)
        .map(CompatibilityFinding::message)
        .findFirst();
  }

  private static Stream<String> names(final Tool tool) {
    return Stream.concat(
        Stream.of(tool.artifact()),
        Stream.ofNullable(PomTools.ENGINES.get(tool.artifact())).map(PomTools.Engine::name));
  }
}
