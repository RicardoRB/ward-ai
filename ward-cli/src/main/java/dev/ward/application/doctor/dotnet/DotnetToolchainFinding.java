package dev.ward.application.doctor.dotnet;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.doctor.ToolchainContext;
import dev.ward.application.doctor.ToolchainFinding;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.Project;
import java.util.List;

/** Checks that the .NET SDK is on the path, and says how to install it when it is not. */
public final class DotnetToolchainFinding implements ToolchainFinding {
  @Override
  public List<Finding> findings(
      final Project detected,
      final EnvironmentFacts facts,
      final ToolchainContext context
  ) {
    return List.of(
        facts.hasExecutable("dotnet")
            ? new Finding("dotnet", Status.OK, "dotnet available.")
            : new Finding(
                "dotnet",
                Status.ERROR,
                "dotnet is required on PATH; install the .NET SDK from"
                    + " https://dotnet.microsoft.com/download."
            )
    );
  }
}
