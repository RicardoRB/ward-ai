package dev.ward.application.init.rust;

import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.init.InitFixtures;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.init.rust.CargoBuildToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Initializes a Cargo project in a directory and reads back the {@code ward.toml} it wrote. */
public final class RustInitFixtures {
  private RustInitFixtures() {}

  /** Writes a one-crate project to {@code root}, runs init with {@code preset}, reads the result. */
  public static WardConfiguration initialize(final Path root, final QualityPreset preset)
      throws java.io.IOException {
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("Cargo.toml"), "[package]\nname = \"app\"\nversion = \"0.1.0\"\n");
    Files.writeString(root.resolve("src/lib.rs"), "pub fn one() -> u8 { 1 }\n");
    new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(),
        new InitSetupCatalog(new FakeEnvironmentInspection("cargo", "cargo-llvm-cov", "cargo-mutants")),
        InitFixtures.projectFiles(
            new CargoBuildToolConfigurator(),
            RuleFileTargetsFixtures.writer()
        )
    )
        .initialize(
            root,
            new InitSelection(
                List.of("none"),
                RustGateIds.forPreset(preset),
                "layered",
                preset,
                List.of(),
                false,
                null,
                false,
                null,
                true,
                false
            )
        );
    return new TomlConfigurationReader().read(root.resolve("ward.toml"));
  }
}
