package dev.ward.application.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.buildpolicy.BuildRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CargoBuildPolicyTest {
  private static final String HEAD = "[package]\nname = \"app\"\nversion = \"0.1.0\"\n\n";

  @TempDir Path root;

  private List<String> run(final BuildRule rule, final String cargo) throws IOException {
    Files.writeString(root.resolve("Cargo.toml"), cargo);
    return BuildPolicyFixtures.findings(root, rule, Map.of());
  }

  @Test
  @DisplayName("a dynamic version")
  void aDynamicVersion() throws IOException {
    assertThat(run(BuildRule.NO_DYNAMIC_VERSION, HEAD + "[dependencies]\nserde = \"*\"\n"))
        .containsExactly("Cargo.toml:6");
  }

  @Test
  @DisplayName("a duplicate declaration")
  void aDuplicateDeclaration() throws IOException {
    assertThat(
        run(
            BuildRule.DUPLICATE_DEPENDENCY_DECLARATION,
            HEAD
                + "[dependencies]\nserde = \"=1.0.0\"\nother = { package = \"serde\","
                + " version = \"=1.0.0\" }\n"
        )
    )
        .containsExactly("Cargo.toml:6", "Cargo.toml:7");
  }

  @Test
  void aCaretRangeIsDynamicUntilCargoLockPinsIt() throws IOException {
    final String cargo = HEAD + "[dependencies]\nserde = \"1.0\"\n";
    assertThat(run(BuildRule.NO_DYNAMIC_VERSION, cargo)).containsExactly("Cargo.toml:6");
    Files.writeString(root.resolve("Cargo.lock"), "version = 3\n");
    assertThat(run(BuildRule.NO_DYNAMIC_VERSION, cargo)).isEmpty();
  }

  @Test
  void aPartialWildcardStaysDynamicWithALockfile() throws IOException {
    Files.writeString(root.resolve("Cargo.lock"), "version = 3\n");
    assertThat(run(BuildRule.NO_DYNAMIC_VERSION, HEAD + "[dependencies]\nserde = \"1.*\"\n"))
        .containsExactly("Cargo.toml:6");
  }

  @Test
  void workspaceTargetAndBuildTablesAreRead() throws IOException {
    Files.createDirectories(root.resolve("crates/a"));
    Files.writeString(
        root.resolve("crates/a/Cargo.toml"),
        HEAD + "[dependencies]\nlocal = { path = \"../b\" }\nshared = { workspace = true }\n"
            + "[target.'cfg(unix)'.dependencies]\nlibc = { version = \"*\" }\n"
            + "[build-dependencies]\ncc = \"*\"\n"
    );
    final String cargo =
        "[workspace]\nmembers = [\"crates/*\"]\n[workspace.dependencies]\nshared = \"*\"\n";
    assertThat(run(BuildRule.NO_DYNAMIC_VERSION, cargo))
        .containsExactlyInAnyOrder(
            "Cargo.toml:4",
            "crates/a/Cargo.toml:9",
            "crates/a/Cargo.toml:11"
        );
  }

  @Test
  void aMalformedCargoFileIsAnError() throws IOException {
    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> run(BuildRule.NO_DYNAMIC_VERSION, "[package\n")
    )
        .hasMessageContaining("Cargo.toml");
  }

  @Test
  void aVersionlessOrOtherTypedDependencyIsIgnored() throws IOException {
    assertThat(
        run(
            BuildRule.NO_DYNAMIC_VERSION,
            HEAD + "[dependencies]\ngit = { git = \"https://example.invalid/x\" }\nn = 3\n"
        )
    )
        .isEmpty();
  }
}
