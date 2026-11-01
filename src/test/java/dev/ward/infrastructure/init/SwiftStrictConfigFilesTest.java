package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writes the strict Swift SwiftLint config, and the strict layer over a user's own config. */
class SwiftStrictConfigFilesTest {
  @TempDir Path root;

  @Test
  void strictWritesTheStrictRulesIntoTheWardOwnedConfig() {
    final NodeConfigTarget target =
        SwiftConfigFiles.targets(swift(), QualityPreset.STRICT).getFirst();

    assertThat(target.disposition()).isEqualTo(NodeConfigTarget.Disposition.WRITE);
    assertThat(target.content())
        .startsWith("# managed-by: ward")
        .contains("  - force_unwrapping")
        .contains("disabled_rules:\n  - trailing_comma\n")
        .contains("cyclomatic_complexity:\n  warning: 10\n");
  }

  @Test
  void strictWithAUserConfigKeepsItAndWritesTheLayerAfterIt() throws IOException {
    final String user = "disabled_rules:\n  - line_length\n";
    Files.writeString(root.resolve(".swiftlint.yml"), user);

    final List<NodeConfigTarget> targets = SwiftConfigFiles.targets(swift(), QualityPreset.STRICT);
    final NodeConfigTarget layer = targets.get(1);

    assertThat(targets.getFirst().disposition()).isEqualTo(NodeConfigTarget.Disposition.KEEP);
    assertThat(layer.file()).isEqualTo(root.resolve(".ward/swiftlint-strict.yml"));
    assertThat(layer.disposition()).isEqualTo(NodeConfigTarget.Disposition.WRITE);
    assertThat(layer.content())
        .startsWith("# managed-by: ward")
        .contains("  - force_unwrapping")
        .doesNotContain("excluded");
    assertThat(Files.readString(root.resolve(".swiftlint.yml"))).isEqualTo(user);
  }

  @Test
  void standardAfterStrictRemovesTheLayerWhenItExists() throws IOException {
    Files.createDirectories(root.resolve(".ward"));
    Files.writeString(root.resolve(".ward/swiftlint-strict.yml"), "# managed-by: ward\n");
    Files.writeString(root.resolve(".swiftlint.yml"), "disabled_rules:\n  - line_length\n");

    final NodeConfigTarget layer = SwiftConfigFiles.targets(swift(), QualityPreset.STANDARD).get(1);

    assertThat(layer.disposition()).isEqualTo(NodeConfigTarget.Disposition.REMOVE);
    assertThat(layer.file()).isEqualTo(root.resolve(".ward/swiftlint-strict.yml"));
  }

  @Test
  void standardWithoutALayerPlansNoLayerFile() {
    assertThat(SwiftConfigFiles.targets(swift(), QualityPreset.STANDARD))
        .extracting(NodeConfigTarget::file)
        .doesNotContain(root.resolve(".ward/swiftlint-strict.yml"));
  }

  private Project swift() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}
