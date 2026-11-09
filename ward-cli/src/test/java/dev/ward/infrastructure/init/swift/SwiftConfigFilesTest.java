package dev.ward.infrastructure.init.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Removes the SwiftLint files an earlier init wrote, and keeps the user's own. */
class SwiftConfigFilesTest {
  private static final String WARD_CONFIG = "# managed-by: ward\nexcluded:\n  - .build\n";
  private static final String USER_CONFIG = "disabled_rules:\n  - line_length\n";

  @TempDir Path root;

  @Test
  void aProjectWithoutEarlierFilesPlansNothing() throws IOException {
    assertThat(SwiftConfigFiles.targets(swift())).isEmpty();
  }

  @Test
  void removesTheWardOwnedSwiftLintConfigAndStrictLayer() throws IOException {
    write(".swiftlint.yml", WARD_CONFIG);
    write(".ward/swiftlint-strict.yml", "# managed-by: ward\n");

    final List<ConfigTarget> targets = SwiftConfigFiles.targets(swift());

    assertThat(targets)
        .extracting(ConfigTarget::file, ConfigTarget::disposition)
        .containsExactly(
            tuple(root.resolve(".swiftlint.yml"), ConfigTarget.Disposition.REMOVE),
            tuple(root.resolve(".ward/swiftlint-strict.yml"), ConfigTarget.Disposition.REMOVE));
  }

  @Test
  void keepsAUserSwiftLintConfigAsWritten() throws IOException {
    write(".swiftlint.yml", USER_CONFIG);

    assertThat(SwiftConfigFiles.targets(swift()))
        .singleElement()
        .satisfies(
            target -> {
              assertThat(target.disposition()).isEqualTo(ConfigTarget.Disposition.KEEP);
              assertThat(target.detail()).startsWith("kept: your .swiftlint.yml");
            });
  }

  @Test
  void removesTheWardOwnedToolsDirectoryWithItsLockFileAndCache() throws IOException {
    write(".ward/swift-tools/Package.swift", "// managed-by: ward\n");
    write(".ward/swift-tools/Package.resolved", "{}\n");

    assertThat(SwiftConfigFiles.targets(swift()))
        .singleElement()
        .satisfies(
            target -> {
              assertThat(target.file()).isEqualTo(root.resolve(".ward/swift-tools"));
              assertThat(target.disposition()).isEqualTo(ConfigTarget.Disposition.REMOVE);
            });
  }

  @Test
  void keepsALockFileWhoseManifestIsTheUsersOwn() throws IOException {
    write(".ward/swift-tools/Package.swift", "// the user's own manifest\n");
    write(".ward/swift-tools/Package.resolved", "{}\n");

    assertThat(SwiftConfigFiles.targets(swift()))
        .extracting(ConfigTarget::disposition)
        .containsExactly(ConfigTarget.Disposition.KEEP);
  }

  private void write(final String name, final String content) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private Project swift() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}
