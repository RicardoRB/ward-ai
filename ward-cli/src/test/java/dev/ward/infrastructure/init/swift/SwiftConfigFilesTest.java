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

/** Writes the Swift tool configs with the indentation of the sources, and keeps the user's own. */
class SwiftConfigFilesTest {
  @TempDir Path root;

  @Test
  void fourSpaceSourcesGetFourSpaces() throws IOException {
    writeSource("App.swift", "struct A {\n    func f() {\n        print(1)\n    }\n}\n");

    assertThat(SwiftConfigFiles.indentation(root)).isEqualTo("4");
  }

  @Test
  void twoSpaceSourcesGetTwoSpaces() throws IOException {
    writeSource("App.swift", "struct A {\n  func f() {\n    print(1)\n  }\n}\n");

    assertThat(SwiftConfigFiles.indentation(root)).isEqualTo("2");
  }

  @Test
  void tabSourcesGetTabs() throws IOException {
    writeSource("App.swift", "struct A {\n\tfunc f() {\n\t\tprint(1)\n\t}\n}\n");

    assertThat(SwiftConfigFiles.indentation(root)).isEqualTo("tabs");
  }

  @Test
  void noSourcesDefaultToFourSpaces() {
    assertThat(SwiftConfigFiles.indentation(root)).isEqualTo("4");
  }

  @Test
  void writesSwiftFormatWithTheDetectedIndentation() throws IOException {
    writeSource("App.swift", "struct A {\n  func f() {}\n}\n");

    final ConfigTarget target = SwiftConfigFiles.targets(swift(), QualityPreset.STANDARD).get(1);

    assertThat(target.disposition()).isEqualTo(ConfigTarget.Disposition.WRITE);
    assertThat(target.content()).isEqualTo("{\"version\": 1, \"indentation\": {\"spaces\": 2}}\n");
  }

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

  @Test
  void keepsAnExistingSwiftFormatUnchanged() throws IOException {
    Files.writeString(root.resolve(".swift-format"), "{\"version\": 1}\n");

    assertThat(SwiftConfigFiles.targets(swift(), QualityPreset.STANDARD).get(1).disposition())
        .isEqualTo(ConfigTarget.Disposition.KEEP);
  }

  private void writeSource(final String name, final String content) throws IOException {
    Files.createDirectories(root.resolve("Sources"));
    Files.writeString(root.resolve("Sources").resolve(name), content);
  }

  private Project swift() {
    return new Project(root, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
  }
}
