package dev.ward.application.init;

import dev.ward.application.init.openspec.OpenSpecInstallation;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OpenSpecDistribution;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.PlannedGateInstallation;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Builds the plan of everything initialization would change, without changing anything. */
final class InitPlanner {
  private final Supplier<InitSetupCatalog> catalog;
  private final IntegrationInstaller installer;
  private final InitProjectFiles files;
  // A toolchain's members reuse this planner's build-file and rule-file changes.
  private final MemberPlanning memberPlanning =
      new MemberPlanning() {
        @Override
        public List<PlannedFileChange> buildFiles(
            final Project project,
            final List<InitGateOption> gates,
            final QualityPreset preset,
            final boolean approved) {
          return InitPlanner.this.buildFiles(project, gates, preset, approved);
        }

        @Override
        public List<PlannedFileChange> ruleFiles(
            final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
          return files.ruleFiles().plan(project, preset, gates);
        }

        @Override
        public InitInputs inputs() {
          return catalog.get().inputs;
        }
      };

  InitPlanner(
      final Supplier<InitSetupCatalog> catalog,
      final IntegrationInstaller installer,
      final InitProjectFiles files) {
    this.catalog = catalog;
    this.installer = installer;
    this.files = files;
  }

  /** Lists the changes in the order {@code InitChanges.apply} makes them. */
  InitPlan plan(final Project project, final InitSelection selection, final boolean approved) {
    installer.validate(selection.integrations());
    final boolean installs = selection.installTools().contains(OpenSpecInstallation.TOOL);
    final String executable =
        installs ? OpenSpecDistribution.executable(Path.of("")).toString() : "openspec";
    final List<InitGateOption> gates =
        SelectedGates.resolve(catalog.get(), project, selection, executable);
    final List<PlannedGateInstallation> gateInstallations = gateInstallations(project, gates);
    final List<PlannedFileChange> changes = new ArrayList<>();
    changes.add(
        configuration(
            project,
            project.root().resolve("ward.toml"),
            gates,
            selection.preset(),
            WardArchitecture.of(selection, project, files.architectureTables())));
    changes.addAll(installer.plan(project, selection.integrations()));
    if (selection.qualityHooks()) {
      changes.addAll(catalog.get().qualityHooks().plan(project));
    }
    changes.addAll(
        InitToolchains.find(project)
            .map(
                support ->
                    support.planMembers(
                        project, gates, selection.preset(), approved, memberPlanning))
            .orElse(List.of()));
    changes.addAll(buildFiles(project, gates, selection.preset(), approved));
    final List<PlannedFileChange> ruleFiles =
        files.ruleFiles().plan(project, selection.preset(), gates);
    changes.addAll(ruleFiles);
    changes.addAll(ownedSectionChanges(project, selection, gates, ruleFiles));
    return new InitPlan(
        changes,
        installs
            ? Optional.of(OpenSpecDistribution.installation(project.root()))
            : Optional.empty(),
        gateInstallations,
        InitToolchains.find(project)
            .map(
                support ->
                    support.scriptReplacements(project, selection.preset(), catalog.get().inputs))
            .orElse(List.of()));
  }

  /** Lists a project's build file, then each module build script that would change. */
  private List<PlannedFileChange> buildFiles(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    final List<PlannedFileChange> changes = new ArrayList<>();
    changes.add(files.buildTools().plan(project, gates, preset, approved));
    changes.addAll(catalog.get().inputs.modules().planModules(project, gates, preset, approved));
    return changes;
  }

  private List<PlannedGateInstallation> gateInstallations(
      final Project project, final List<InitGateOption> gates) {
    return gates.stream()
        .map(gate -> catalog.get().gateInstallation(project, gate))
        .flatMap(Optional::stream)
        .toList();
  }

  /**
   * Plans the Ward-owned sections, and the {@code .editorconfig} that {@code ward-format} writes
   * when the project has none. Rule files that the user owns are kept as they are.
   */
  private List<PlannedFileChange> ownedSectionChanges(
      final Project project,
      final InitSelection selection,
      final List<InitGateOption> gates,
      final List<PlannedFileChange> ruleFiles) {
    final List<Path> userOwned =
        ruleFiles.stream()
            .filter(file -> file.action() == PlannedFileChange.Action.PRESERVE_USER_OWNED)
            .map(PlannedFileChange::file)
            .toList();
    final List<OwnedSection> sections =
        OwnedSections.withEditorConfig(
            OwnedSections.of(catalog.get(), project, selection, gates, userOwned),
            project,
            gates,
            files.configuration().read(OwnedSections.editorConfigPath(project)));
    final List<PlannedFileChange> changes = new ArrayList<>();
    sections.stream().map(files.sections()::plan).forEach(changes::add);
    changes.addAll(existingEditorConfig(project, gates));
    return changes;
  }

  /**
   * Names the project's own {@code .editorconfig} in the preview when {@code ward-format} formats
   * with it: init leaves that file byte for byte, so it is planned as unchanged.
   */
  private List<PlannedFileChange> existingEditorConfig(
      final Project project, final List<InitGateOption> gates) {
    final Path file = EditorConfigTemplate.path(project);
    if (gates.stream().noneMatch(gate -> FormatGates.WARD_FORMAT.equals(gate.id()))
        || files.configuration().read(file).isEmpty()) {
      return List.of();
    }
    // Kept rather than unchanged, so the preview, which hides unchanged files, still shows it.
    return List.of(
        new PlannedFileChange(
            file,
            PlannedFileChange.Action.PRESERVE_USER_OWNED,
            List.of(
                "Ward formats with this .editorconfig; init leaves it byte for byte, and keys it"
                    + " leaves unset use Ward's defaults.")));
  }

  private PlannedFileChange configuration(
      final Project project,
      final Path file,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final WardArchitecture architecture) {
    final Optional<String> existing = files.configuration().read(file);
    final List<String> notes = OmissionNotes.of(catalog.get(), project, preset);
    if (existing.isEmpty()) {
      return new PlannedFileChange(file, PlannedFileChange.Action.CREATE, notes);
    }
    return new PlannedFileChange(
        file,
        ConfigurationTemplate.render(
                    gates,
                    preset,
                    project,
                    catalog.get().openSpecDirectories().root(project),
                    architecture)
                .equals(existing.get())
            ? PlannedFileChange.Action.UNCHANGED
            : PlannedFileChange.Action.MODIFY,
        notes);
  }
}
