package dev.ward.application.doctor.rust;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.doctor.ToolchainContext;
import dev.ward.application.doctor.ToolchainFinding;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.Project;
import java.util.List;

/** Checks that {@code cargo} is installed, and says how to install it when it is not. */
public final class RustToolchainFinding implements ToolchainFinding {
  private static final String CARGO = "cargo";

  @Override
  public List<Finding> findings(
      final Project detected,
      final EnvironmentFacts facts,
      final ToolchainContext context
  ) {
    return List.of(
        facts.hasExecutable(CARGO)
            ? new Finding(CARGO, Status.OK, "cargo available.")
            : new Finding(
                CARGO,
                Status.ERROR,
                "cargo is required on PATH; install Rust with rustup (https://rustup.rs)."
            )
    );
  }
}
