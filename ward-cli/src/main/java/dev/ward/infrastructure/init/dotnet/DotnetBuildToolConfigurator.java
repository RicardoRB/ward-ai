package dev.ward.infrastructure.init.dotnet;

import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes no build file: the solution and the {@code .csproj} files are never edited, and the gates
 * need only the {@code dotnet} that is already installed.
 */
public final class DotnetBuildToolConfigurator implements BuildToolConfiguration {
  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public DotnetBuildToolConfigurator() {}

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    return new BuildFileChange(Toolchain.DOTNET, null, null, List.of());
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved
  ) {
    return new PlannedFileChange(
        Path.of(project.root().toString()),
        PlannedFileChange.Action.UNCHANGED,
        List.of()
    );
  }

  @Override
  public void rollback(final BuildFileChange change) {
    // .NET init writes no build file, so there is nothing to undo.
  }
}
