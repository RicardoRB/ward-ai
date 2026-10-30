package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.TurboWorkspace;
import dev.ward.domain.init.TurboWorkspaceChange;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.inject.Inject;

/** Routes initialization mutations to the build-file adapter of the project's toolchain. */
public final class BuildToolConfigurator implements BuildToolConfiguration {
  private final Map<Toolchain, BuildToolConfiguration> adapters;

  /** Creates the router over one build-file adapter per supported toolchain. */
  @Inject
  public BuildToolConfigurator(final Map<Toolchain, BuildToolConfiguration> adapters) {
    this.adapters = Map.copyOf(adapters);
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    return adapter(project).apply(project, gates);
  }

  @Override
  public BuildFileChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    return adapter(project).apply(project, gates, preset, approval);
  }

  /** Turborepo workspaces are Node projects, so only the Node adapter writes them. */
  @Override
  public TurboWorkspaceChange applyWorkspace(final TurboWorkspace workspace) {
    final BuildToolConfiguration node = adapters.get(Toolchain.NODE);
    return node == null
        ? new TurboWorkspaceChange(List.of(), List.of())
        : node.applyWorkspace(workspace);
  }

  @Override
  public void rollbackWorkspace(final TurboWorkspaceChange change) {
    final BuildToolConfiguration node = adapters.get(Toolchain.NODE);
    if (node != null) {
      node.rollbackWorkspace(change);
    }
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    return adapter(project).plan(project, gates, preset, approved);
  }

  @Override
  public List<GateOmission> omissions(final Project project, final List<String> gateIds) {
    return adapter(project).omissions(project, gateIds);
  }

  @Override
  public List<GateOmission> unsupportedPins(final Project project, final List<String> gateIds) {
    return adapter(project).unsupportedPins(project, gateIds);
  }

  @Override
  public Optional<Integer> runtimeJdk(final Project project) {
    return adapter(project).runtimeJdk(project);
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || change.file() == null) {
      return;
    }
    final BuildToolConfiguration adapter = adapters.get(change.toolchain());
    if (adapter == null) {
      throw new WardException(
          "Ward cannot roll back " + change.toolchain() + " build files: no adapter is available.");
    }
    adapter.rollback(change);
  }

  private BuildToolConfiguration adapter(final Project project) {
    final BuildToolConfiguration adapter = adapters.get(project.toolchain());
    if (adapter == null) {
      throw new WardException(
          "Ward does not configure "
              + project.buildTool().displayName()
              + " build files; ward init supports Maven, Gradle, TypeScript, JavaScript and Python projects.");
    }
    return adapter;
  }
}
