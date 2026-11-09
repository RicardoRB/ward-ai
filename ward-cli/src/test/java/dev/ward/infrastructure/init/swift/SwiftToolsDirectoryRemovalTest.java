package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.RuleFileTargetsFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Re-running init removes Ward's earlier SwiftLint tools directory, and nothing it does not own.
 */
class SwiftToolsDirectoryRemovalTest {
  private static final PresetRuleFileWriter WRITER =
      new PresetRuleFileWriter(RuleFileTargetsFixtures.defaults());

  @Test
  void wardOwnedToolsDirectoryIsRemovedWithItsBuildCache(@TempDir final Path root)
      throws IOException {
    write(root, ".ward/swift-tools/Package.swift", "// managed-by: ward\n");
    write(root, ".ward/swift-tools/Package.resolved", "{}\n");
    write(root, ".ward/swift-tools/.build/artifacts/swiftlint", "binary");
    write(root, ".ward/reports/tests.json", "{}\n");

    final List<PlannedFileChange> plan =
        WRITER.plan(swift(root), QualityPreset.STANDARD, List.of());
    assertThat(plan)
        .anySatisfy(
            change -> {
              assertThat(change.file()).isEqualTo(root.resolve(".ward/swift-tools"));
              assertThat(change.action()).isEqualTo(Action.REMOVE);
            });

    WRITER.apply(swift(root), QualityPreset.STANDARD, List.of());

    assertThat(root.resolve(".ward/swift-tools")).doesNotExist();
    assertThat(root.resolve(".ward/reports/tests.json")).exists();
  }

  @Test
  void toolsDirectoryWithAUserManifestIsKept(@TempDir final Path root) throws IOException {
    write(root, ".ward/swift-tools/Package.swift", "// the user's own manifest\n");
    write(root, ".ward/swift-tools/Package.resolved", "{}\n");

    WRITER.apply(swift(root), QualityPreset.STANDARD, List.of());

    assertThat(Files.readString(root.resolve(".ward/swift-tools/Package.swift")))
        .isEqualTo("// the user's own manifest\n");
    assertThat(root.resolve(".ward/swift-tools/Package.resolved")).exists();
  }

  private static void write(final Path root, final String name, final String content)
      throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static Project swift(final Path root) {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}
