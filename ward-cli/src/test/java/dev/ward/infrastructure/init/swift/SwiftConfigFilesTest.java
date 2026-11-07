package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writes the Swift tool configs, and keeps the user's own. */
class SwiftConfigFilesTest {
  @TempDir Path root;

  @Test
  void writesWardMarkedSwiftLintConfigWhenTheProjectHasNone() {
    final ConfigTarget target =
        SwiftConfigFiles.targets(swift(), QualityPreset.STANDARD).getFirst();

    assertThat(target.disposition()).isEqualTo(ConfigTarget.Disposition.WRITE);
    assertThat(target.content()).startsWith("# managed-by: ward").contains("  - .ward");
  }

  @Test
  void keepsAUserSwiftLintConfigUnchanged() throws IOException {
    Files.writeString(root.resolve(".swiftlint.yml"), "disabled_rules:\n  - line_length\n");

    assertThat(SwiftConfigFiles.targets(swift(), QualityPreset.STANDARD).getFirst().disposition())
        .isEqualTo(ConfigTarget.Disposition.KEEP);
  }

  private Project swift() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}
