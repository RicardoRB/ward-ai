package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Swift projects get the ignore lines for Ward's reports and tools build, and the agent rules. */
class SwiftInitFilesTest {
  private static final Path ROOT = Path.of("/shop");
  private static final Project SWIFT_PM =
      new Project(ROOT, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);

  @Test
  void swiftProjectIgnoresReportsAndTheToolsBuild() {
    final InitSetupCatalog catalog =
        new InitSetupCatalog(
            new FakeEnvironmentInspection(),
            new NodeInputs(
                directory -> PackageManifest.EMPTY,
                directory -> false,
                (directory, gate) -> Optional.empty()),
            null,
            null,
            "dev");
    final InitSelection selection =
        new InitSelection(
            List.of("none"),
            List.of(),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            false,
            null,
            false,
            null);

    assertThat(
            OwnedSections.of(catalog, SWIFT_PM, selection, swiftGates(), List.of()).stream()
                .map(OwnedSection::body)
                .toList())
        .anyMatch(content -> content.contains(".ward/swift-tools/.build/"));
  }

  @Test
  void agentRulesNameTheSuppressionsAndTheEightyPercentMinimum() {
    final List<String> lines = SwiftAgentRules.lines(swiftGates());

    assertThat(String.join("\n", lines))
        .contains("// swiftlint:disable")
        .contains("// swift-format-ignore")
        .contains("80% minimum");
  }

  private static List<InitGateOption> swiftGates() {
    return List.of(
        new InitGateOption("swift-format", List.of("swift", "format", "lint"), true, "ok"),
        new InitGateOption(
            "swift-coverage",
            "Fails below the line coverage minimum.",
            List.of("swift", "test", "--show-codecov-path"),
            true,
            "ok",
            Map.of("minimum", "80")));
  }
}
