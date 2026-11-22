package dev.ward.application.init.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.init.InitFixtures;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import dev.ward.infrastructure.init.rust.CargoBuildToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.ProjectTree;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The init scenarios of story R1: Cargo crates and workspaces get the Rust gates. */
class RustInitScenariosTest {
  private static final String CRATE = "[package]\nname = \"%s\"\nversion = \"0.1.0\"\n";
  private static final String WORKSPACE = "[workspace]\nmembers = [\"crates/*\"]\n";
  private static final String CLIPPY = "cargo-clippy";
  private static final String TEST = "cargo-test";
  private static final int MEMBERS = 3;

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();

  @Test
  @DisplayName("a single Rust project")
  void aSingleRustProject() throws IOException {
    final ProjectTree tree = new ProjectTree(root);
    tree.write("Cargo.toml", CRATE.formatted("app"));
    tree.write("src/lib.rs", "pub fn one() -> u8 { 1 }\n");

    final Project project = inspection.detect(root).orElseThrow();
    assertThat(project.language()).isEqualTo(Language.RUST);
    assertThat(project.buildTool()).isEqualTo(BuildTool.CARGO);

    initialize(QualityPreset.STANDARD);

    final WardConfiguration configuration =
        new TomlConfigurationReader().read(root.resolve("ward.toml"));
    assertThat(configuration.checks()).containsKeys(CLIPPY, TEST, "ward");
    assertThat(configuration.checks().get(TEST).command())
        .containsExactly("cargo", "test", "--workspace");
    assertThat(configuration.checks().get(CLIPPY).command())
        .containsExactly("cargo", "clippy", "--workspace", "--all-targets", "--", "-D", "warnings");
    assertThat(configuration.forProfiles(List.of("pre-push")).checks()).containsKeys(CLIPPY, TEST);
  }

  @Test
  @DisplayName("a Rust workspace")
  void aRustWorkspace() throws IOException {
    final ProjectTree tree = new ProjectTree(root);
    tree.write("Cargo.toml", WORKSPACE);
    for (final String member : List.of("core", "api", "cli")) {
      tree.write("crates/" + member + "/Cargo.toml", CRATE.formatted(member));
      tree.write("crates/" + member + "/src/lib.rs", "pub fn one() -> u8 { 1 }\n");
    }

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path(""));
    assertThat(inspection.discover(root).getFirst().moduleCount()).isEqualTo(MEMBERS);
    assertThat(inspection.detect(tree.path("crates/api")).orElseThrow().root())
        .isEqualTo(tree.path(""));

    initialize(QualityPreset.STRICT);

    final String configuration = new TomlConfigurationReader().read(root.resolve("ward.toml"))
        .checks()
        .get(TEST)
        .command()
        .toString();
    assertThat(configuration).contains("--workspace");
    assertThat(java.nio.file.Files.readString(root.resolve("ward.toml")))
        .contains("crates/core/src", "crates/api/src", "crates/cli/src");
  }

  private void initialize(final QualityPreset preset) {
    new InitService(
        new ProjectDetector(inspection),
        Set.of(),
        new InitSetupCatalog(new FakeEnvironmentInspection("cargo")),
        InitFixtures.projectFiles(
            new CargoBuildToolConfigurator(),
            RuleFileTargetsFixtures.writer()
        )
    )
        .initialize(
            root,
            new InitSelection(
                List.of("none"),
                List.of(CLIPPY, TEST),
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
  }
}
