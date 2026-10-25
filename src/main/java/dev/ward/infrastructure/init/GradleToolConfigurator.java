package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.GradleToolCatalog;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Adds conservative, reversible Gradle configuration for selected init gates: plugin declarations
 * inside the {@code plugins {}} block, and blocks that wire the plugins to the generated rule files
 * and thresholds.
 */
public final class GradleToolConfigurator implements BuildToolConfiguration {
  /** Plugins in declaration order; a versioned plugin takes its version from the catalog. */
  private static final List<Plugin> PLUGINS =
      List.of(
          new Plugin("format", GradleToolCatalog.SPOTLESS, true),
          new Plugin("checkstyle", "checkstyle", false),
          new Plugin("pmd", "pmd", false),
          new Plugin("spotbugs", GradleToolCatalog.SPOTBUGS, true),
          new Plugin("coverage", "jacoco", false),
          new Plugin("dependency-check", "org.owasp.dependencycheck", true),
          new Plugin("mutation", GradleToolCatalog.PITEST, true),
          new Plugin("sonar", "org.sonarqube", true),
          new Plugin("license", "com.github.jk1.dependency-license-report", true),
          new Plugin("api-compat", "me.champeau.gradle.japicmp", true));

  private final Optional<CompatibilityFindings> compatibility;

  /**
   * Creates the adapter that Dagger injects; {@code inspection} reads the Gradle version and the
   * launcher JVM to choose compatible versions.
   */
  @Inject
  public GradleToolConfigurator(@Named("gradle") final CompatibilityInspection inspection) {
    this.compatibility =
        Optional.of(new CompatibilityFindings(inspection, GradleToolCatalog.entries()));
  }

  /** Creates an adapter that writes each tool's newest catalog version without inspecting. */
  public GradleToolConfigurator() {
    this.compatibility = Optional.empty();
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    return apply(project, gates, null, finding -> false);
  }

  @Override
  public BuildFileChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    final Computed computed = compute(project, gates, preset);
    final GradleBuildFileEdit edit = computed.edit();
    if (!edit.tools().isEmpty()) {
      try {
        Files.writeString(
            edit.buildFile(),
            edit.content(),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
      } catch (IOException e) {
        throw new WardException(
            "Could not configure Gradle tools in " + edit.buildFile() + ": " + e.getMessage(), e);
      }
    }
    return new BuildFileChange(
        Toolchain.JVM, edit.buildFile(), edit.original(), edit.tools(), computed.warnings());
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final Computed computed = compute(project, gates, preset);
    final List<String> details = new ArrayList<>(computed.warnings());
    details.addAll(computed.edit().details());
    return new PlannedFileChange(
        computed.edit().buildFile(),
        computed.edit().details().isEmpty()
            ? PlannedFileChange.Action.UNCHANGED
            : PlannedFileChange.Action.MODIFY,
        details);
  }

  @Override
  public List<GateOmission> omissions(final Project project, final List<String> gateIds) {
    return compatibility
        .map(
            resolver ->
                resolver.omissions(project, GradleToolVersions.toolsByGate(Set.copyOf(gateIds))))
        .orElse(List.of());
  }

  @Override
  public List<GateOmission> unsupportedPins(final Project project, final List<String> gateIds) {
    return compatibility
        .map(
            resolver ->
                resolver.unsupportedPins(
                    project, GradleToolVersions.toolsByGate(Set.copyOf(gateIds))))
        .orElse(List.of());
  }

  @Override
  public Optional<Integer> runtimeJdk(final Project project) {
    return compatibility.flatMap(resolver -> resolver.runtimeJdk(project));
  }

  /** Builds the edited build file in memory; shared by {@link #apply} and {@link #plan}. */
  private Computed compute(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    final Path buildFile = buildFile(project.root());
    final Set<String> requested =
        gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final GradleToolVersions versions =
        GradleToolVersions.resolve(compatibility, project, requested);
    final GradleBuildFileEdit edit;
    try {
      edit = new GradleBuildFileEdit(buildFile, Files.readString(buildFile));
    } catch (IOException e) {
      throw new WardException(
          "Could not configure Gradle tools in " + buildFile + ": " + e.getMessage(), e);
    }
    final List<String> warnings = new ArrayList<>(versions.warning().stream().toList());
    requested.stream()
        .sorted()
        .forEach(
            gate ->
                versions
                    .unavailable(gate)
                    .ifPresent(reason -> warnings.add(gate + " omitted: " + reason)));
    final Set<String> selected =
        requested.stream()
            .filter(gate -> versions.unavailable(gate).isEmpty())
            .collect(Collectors.toSet());
    final boolean kotlin = Files.isRegularFile(project.root().resolve("build.gradle.kts"));
    final List<String> declarations = new ArrayList<>();
    for (final Plugin plugin : PLUGINS) {
      if (selected.contains(plugin.gate()) && !edit.contains(plugin.id())) {
        final String version = plugin.versioned() ? versions.version(plugin.id()) : "";
        declarations.add(plugin.declaration(kotlin, version));
        edit.record(plugin.id(), version);
      }
    }
    edit.addPlugins(declarations);
    GradleBuildBlocks.append(edit, selected, preset, kotlin, versions);
    return new Computed(edit, warnings);
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || !change.changed()) {
      return;
    }
    try {
      Files.writeString(
          change.file(),
          change.originalContent(),
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new WardException("Could not restore " + change.file() + ": " + e.getMessage(), e);
    }
  }

  private static Path buildFile(final Path root) {
    final Path kotlin = root.resolve("build.gradle.kts");
    final Path groovy = root.resolve("build.gradle");
    if (Files.isRegularFile(kotlin)) {
      return kotlin;
    }
    if (Files.isRegularFile(groovy)) {
      return groovy;
    }
    throw new WardException(
        "Could not configure Gradle tools: no build.gradle or build.gradle.kts found.");
  }

  /** The edited build file and the warnings for the init output. */
  private record Computed(GradleBuildFileEdit edit, List<String> warnings) {}

  private record Plugin(String gate, String id, boolean versioned) {
    String declaration(final boolean kotlin, final String version) {
      if (kotlin) {
        return "id(\"" + id + "\")" + (version.isBlank() ? "" : " version \"" + version + "\"");
      }
      return "id '" + id + "'" + (version.isBlank() ? "" : " version '" + version + "'");
    }
  }
}
