package dev.ward.infrastructure.init.rust;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.List;
import javax.inject.Inject;

/**
 * Writes no build file for Rust: the project's {@code Cargo.toml} is never edited, and the gates
 * need only the {@code cargo} that is already installed.
 */
public final class CargoBuildToolConfigurator implements BuildToolConfiguration {
  private static final String MANIFEST = "Cargo.toml";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public CargoBuildToolConfigurator() {}

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    return new BuildFileChange(Toolchain.RUST, null, null, List.of());
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved
  ) {
    return new PlannedFileChange(
        project.root().resolve(MANIFEST),
        PlannedFileChange.Action.UNCHANGED,
        List.of()
    );
  }

  @Override
  public void rollback(final BuildFileChange change) {
    // Rust init writes no build file, so there is nothing to undo.
  }
}
