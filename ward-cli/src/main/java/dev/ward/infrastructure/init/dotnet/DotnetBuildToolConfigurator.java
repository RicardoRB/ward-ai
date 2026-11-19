package dev.ward.infrastructure.init.dotnet;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Edits no solution or {@code .csproj}. When the Stryker.NET gate is selected it writes the tool
 * manifest {@code .config/dotnet-tools.json} that pins {@code dotnet-stryker}, unless the project
 * already has one, which stays as it is.
 */
public final class DotnetBuildToolConfigurator implements BuildToolConfiguration {
  static final String MANIFEST = ".config/dotnet-tools.json";
  static final String STRYKER = "dotnet-stryker";

  private static final Logger LOGGER =
      Logger.getLogger(DotnetBuildToolConfigurator.class.getName());
  private static final String CONTENT =
      """
      {
        "version": 1,
        "isRoot": true,
        "tools": {
          "dotnet-stryker": {
            "version": "4.16.0",
            "commands": ["dotnet-stryker"]
          }
        }
      }
      """;

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public DotnetBuildToolConfigurator() {}

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    final Path manifest = project.root().resolve(MANIFEST);
    if (!needsManifest(manifest, gates)) {
      return new BuildFileChange(Toolchain.DOTNET, null, null, List.of());
    }
    try {
      Files.createDirectories(manifest.getParent());
      Files.writeString(manifest, CONTENT);
    } catch (IOException e) {
      throw new WardException("Cannot write " + MANIFEST + ": " + e.getMessage(), e);
    }
    LOGGER.log(Level.INFO, "event=dotnet-tool-manifest-written");
    return new BuildFileChange(Toolchain.DOTNET, manifest, null, List.of(STRYKER));
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved
  ) {
    final Path manifest = project.root().resolve(MANIFEST);
    if (needsManifest(manifest, gates)) {
      return new PlannedFileChange(
          manifest,
          PlannedFileChange.Action.CREATE,
          List.of("pin " + STRYKER + " for the " + SupportedQualityGates.STRYKER_NET + " gate")
      );
    }
    return new PlannedFileChange(manifest, PlannedFileChange.Action.UNCHANGED, List.of());
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null || change.file() == null || change.originalContent() != null) {
      return;
    }
    try {
      Files.deleteIfExists(change.file());
    } catch (IOException e) {
      throw new WardException("Cannot remove " + MANIFEST + ": " + e.getMessage(), e);
    }
  }

  private static boolean needsManifest(final Path manifest, final List<InitGateOption> gates) {
    return gates.stream().anyMatch(gate -> SupportedQualityGates.STRYKER_NET.equals(gate.id()))
        && !Files.exists(manifest);
  }
}
