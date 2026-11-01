package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.GradleToolCatalog;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.ScriptEdit;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.project.GradleAndroidModules;
import dev.ward.infrastructure.project.GradleKotlinModules;
import java.nio.file.Path;
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
 * and thresholds. In a multi-module Kotlin build the module-placed gates go into each module
 * script.
 */
public final class GradleToolConfigurator implements BuildToolConfiguration {
  /** Plugins in declaration order; a versioned plugin takes its version from the catalog. */
  private static final List<GradlePlugin> PLUGINS =
      List.of(
          new GradlePlugin("format", GradleToolCatalog.SPOTLESS, true, true),
          new GradlePlugin("checkstyle", "checkstyle", false, true),
          new GradlePlugin("pmd", "pmd", false, true),
          new GradlePlugin("spotbugs", GradleToolCatalog.SPOTBUGS, true, true),
          new GradlePlugin("coverage", "jacoco", false, true),
          new GradlePlugin("dependency-check", "org.owasp.dependencycheck", true, false),
          new GradlePlugin("mutation", GradleToolCatalog.PITEST, true, true),
          new GradlePlugin("sonar", "org.sonarqube", true, false),
          new GradlePlugin("license", "com.github.jk1.dependency-license-report", true, false),
          new GradlePlugin("api-compat", "me.champeau.gradle.japicmp", true, false));

  private static final String NO_KOTLIN_MODULE =
      "No module applies the Kotlin JVM plugin, or the Java plugin with Java sources, directly, so"
          + " the coverage, SpotBugs and mutation gates stay in the root build script.";

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
    GradleScriptFiles.writeAll(computed.edits());
    final GradleBuildFileEdit root = computed.root();
    return new BuildFileChange(
        Toolchain.JVM,
        root.buildFile(),
        root.original(),
        computed.edits().stream().flatMap(edit -> edit.tools().stream()).distinct().toList(),
        computed.warnings(),
        null,
        null,
        computed.modules().stream()
            .filter(module -> !module.tools().isEmpty())
            .map(module -> new ScriptEdit(module.buildFile(), module.original()))
            .toList());
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final Computed computed = compute(project, gates, preset);
    final List<String> details = new ArrayList<>(computed.warnings());
    details.addAll(computed.root().details());
    return new PlannedFileChange(
        computed.root().buildFile(),
        computed.root().details().isEmpty()
            ? PlannedFileChange.Action.UNCHANGED
            : PlannedFileChange.Action.MODIFY,
        details);
  }

  /** Describes the Kotlin module scripts the gates would change, without writing. */
  public List<PlannedFileChange> planModules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    return compute(project, gates, preset).modules().stream()
        .filter(module -> !module.details().isEmpty())
        .map(
            module ->
                new PlannedFileChange(
                    module.buildFile(), PlannedFileChange.Action.MODIFY, module.details()))
        .toList();
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

  /**
   * Builds the edited scripts in memory; shared by {@link #apply}, {@link #plan} and planModules.
   */
  private Computed compute(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    final Path buildFile = GradleScriptFiles.buildFile(project.root());
    final Set<String> requested =
        gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final GradleToolVersions versions =
        GradleToolVersions.resolve(compatibility, project, requested);
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
    final GradleBuildFileEdit root = GradleBuildFileEdit.read(buildFile);
    final List<Path> kotlinModules = GradleKotlinModules.of(project.root());
    final List<Path> androidModules = GradleAndroidModules.of(project.root());
    final boolean rootJvm =
        GradleKotlinModules.appliesJava(root.original())
            || GradleKotlinModules.appliesKotlinJvm(root.original());
    final Set<String> moduleGates = GradleScriptPlacement.moduleGates(selected, kotlinModules);
    final Set<String> rootGates =
        rootJvm || moduleGates.isEmpty()
            ? selected
            : selected.stream()
                .filter(gate -> !moduleGates.contains(gate))
                .collect(Collectors.toSet());
    root.addPlugins(
        GradleScriptPlacement.rootDeclarations(root, PLUGINS, rootGates, moduleGates, versions));
    GradleBuildBlocks.append(root, rootGates, preset, versions, false);
    final List<GradleBuildFileEdit> modules = new ArrayList<>();
    for (final Path module : kotlinModules) {
      modules.add(GradleScriptPlacement.moduleEdit(module, PLUGINS, moduleGates, preset, versions));
    }
    for (final Path module : androidModules) {
      modules.add(GradleScriptPlacement.androidModuleEdit(module, selected));
    }
    GradleScriptPlacement.logPlacement(project, kotlinModules, rootJvm);
    if (kotlinModules.isEmpty()
        && !rootJvm
        && GradleScriptPlacement.hasModuleGate(selected)
        && GradleKotlinModules.hasKotlinSources(project.root())) {
      warnings.add(NO_KOTLIN_MODULE);
    }
    return new Computed(root, modules, warnings);
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || !change.changed()) {
      return;
    }
    GradleScriptFiles.restore(change.file(), change.originalContent());
    change
        .moduleScripts()
        .forEach(script -> GradleScriptFiles.restore(script.file(), script.originalContent()));
  }

  /** The root and module edits, and the warnings for the init output. */
  private record Computed(
      GradleBuildFileEdit root, List<GradleBuildFileEdit> modules, List<String> warnings) {
    List<GradleBuildFileEdit> edits() {
      final List<GradleBuildFileEdit> edits = new ArrayList<>(List.of(root));
      edits.addAll(modules);
      return edits;
    }
  }
}
