package dev.ward.infrastructure.init.maven;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.ManagedPluginCatalog;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.CompatibilityFindings;
import dev.ward.infrastructure.init.maven.PomTools.Tool;
import dev.ward.infrastructure.project.MavenJavaModules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Adds only missing, pinned Maven tool declarations required by selected init gates. In a
 * multi-module build the module-placed gates go into each Java module's pom, and the aggregator
 * pins their versions in {@code pluginManagement}.
 */
public final class PomToolConfigurator implements BuildToolConfiguration {
  private final Optional<CompatibilityFindings> compatibility;

  /**
   * Creates the configurator; {@code compatibilityInspection} reads the toolchain to choose
   * compatible plugin versions.
   */
  @Inject
  public PomToolConfigurator(final CompatibilityInspection compatibilityInspection) {
    this.compatibility =
        Optional.of(
            new CompatibilityFindings(compatibilityInspection, ManagedPluginCatalog.entries()));
  }

  /** Legacy construction retained for focused configurator fixtures. */
  public PomToolConfigurator() {
    this.compatibility = Optional.empty();
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    return apply(project, gates, null);
  }

  @Override
  public BuildFileChange apply(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    return apply(project, gates, preset, finding -> false);
  }

  @Override
  public BuildFileChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    final PomEdit edit = compute(project, gates, preset, approval);
    PomFiles.writeAll(edit);
    return new BuildFileChange(
        Toolchain.JVM,
        edit.pom,
        edit.original,
        edit.allTools(),
        edit.warnings,
        null,
        null,
        edit.moduleScripts());
  }

  /** Describes, without writing, the module poms the gates would change. */
  public List<PlannedFileChange> planModules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    return compute(project, gates, preset, finding -> approved).modules.stream()
        .filter(module -> !module.details.isEmpty())
        .map(
            module ->
                new PlannedFileChange(module.pom, PlannedFileChange.Action.MODIFY, module.details))
        .toList();
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final List<CompatibilityFinding> pending = new ArrayList<>();
    final PomEdit edit =
        compute(
            project,
            gates,
            preset,
            finding -> {
              if (!approved) {
                pending.add(finding);
              }
              return approved;
            });
    final List<String> details = new ArrayList<>(edit.details);
    pending.forEach(
        finding ->
            details.add(
                "~ "
                    + finding.artifactId()
                    + " "
                    + finding.currentVersion()
                    + " -> "
                    + finding.selectedVersion()
                    + " (requires approval)"));
    return new PlannedFileChange(
        edit.pom,
        details.isEmpty() ? PlannedFileChange.Action.UNCHANGED : PlannedFileChange.Action.MODIFY,
        details);
  }

  /**
   * Computes and validates the edited pom in memory; shared by {@link #apply} and {@link #plan}.
   */
  private PomEdit compute(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    final Set<String> selected = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final PomEdit edit = PomEdit.read(project.root().resolve("pom.xml"));
    final PomToolVersions versions = PomToolVersions.resolve(compatibility, project, selected);
    versions
        .warning()
        .ifPresent(
            warning -> {
              edit.warnings.add(warning);
              edit.details.add(warning);
            });
    final List<PomEdit> modules =
        MavenJavaModules.of(project.root()).stream().map(PomEdit::read).toList();
    edit.modules.addAll(modules);
    final Set<String> moduleGates =
        modules.isEmpty()
            ? Set.of()
            : PomTools.TOOLS.stream()
                .filter(Tool::module)
                .map(Tool::gate)
                .filter(selected::contains)
                .collect(Collectors.toSet());
    final Set<String> rootGates =
        selected.stream().filter(gate -> !moduleGates.contains(gate)).collect(Collectors.toSet());
    PomTools.TOOLS.stream()
        .filter(tool -> selected.contains(tool.gate()))
        .forEach(
            tool ->
                versions
                    .unavailable(tool)
                    .ifPresentOrElse(
                        reason -> edit.warnings.add(tool.gate() + " omitted: " + reason),
                        () -> {
                          if (tool.module() && !edit.modules.isEmpty()) {
                            PomModulePlacement.place(tool, preset, versions, edit);
                          } else {
                            PomToolPlacement.apply(tool, preset, versions, approval, edit);
                          }
                        }));
    edit.content = PresetPluginSnippets.pinLifecycle(edit.content, selected, preset, edit.details);
    if (!edit.tools.isEmpty()) {
      PomDeclarationValidator.validate(
          edit.content, PomTools.addedDeclarations(edit.original, edit.content, rootGates));
    }
    edit.modules.forEach(
        module -> {
          if (!module.tools.isEmpty()) {
            PomDeclarationValidator.validate(
                module.content,
                PomTools.addedDeclarations(module.original, module.content, selected));
          }
        });
    return edit;
  }

  @Override
  public List<GateOmission> omissions(final Project project, final List<String> gateIds) {
    return compatibility
        .map(
            resolver ->
                resolver.omissions(project, PomToolVersions.toolsByGate(Set.copyOf(gateIds))))
        .orElse(List.of());
  }

  @Override
  public List<GateOmission> unsupportedPins(final Project project, final List<String> gateIds) {
    return compatibility
        .map(
            resolver ->
                resolver.unsupportedPins(project, PomToolVersions.toolsByGate(Set.copyOf(gateIds))))
        .orElse(List.of());
  }

  @Override
  public Optional<Integer> runtimeJdk(final Project project) {
    return compatibility.flatMap(resolver -> resolver.runtimeJdk(project));
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || !change.changed()) {
      return;
    }
    PomFiles.write(change.file(), change.originalContent());
    change
        .moduleScripts()
        .forEach(module -> PomFiles.write(module.file(), module.originalContent()));
  }
}
