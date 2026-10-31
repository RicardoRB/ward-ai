package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.WorkspaceMembers;
import dev.ward.domain.project.XcodeSchemes;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Scheme selection for Xcode projects: one scheme is automatic, several need --scheme. */
class SwiftSchemeSelectionTest {
  private static final Project XCODE =
      new Project(Path.of("/shop"), Language.SWIFT, BuildTool.XCODE, Framework.NONE);

  @Test
  void aSingleSchemeIsUsedWithoutAsking() {
    assertThat(testCommand(List.of("Shop"), Optional.empty())).contains("-scheme", "Shop");
  }

  @Test
  void theNamedSchemeIsUsedWhenSeveralExist() {
    assertThat(testCommand(List.of("Shop", "ShopWidgets"), Optional.of("ShopWidgets")))
        .containsSequence("-scheme", "ShopWidgets");
  }

  @Test
  void severalSchemesWithoutAChoiceFailNamingThemAndTheOption() {
    assertThatThrownBy(() -> testCommand(List.of("Shop", "ShopWidgets"), Optional.empty()))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Shop, ShopWidgets")
        .hasMessageContaining("--scheme");
  }

  @Test
  void anUnknownSchemeFailsNamingTheSchemes() {
    assertThatThrownBy(() -> testCommand(List.of("Shop", "ShopWidgets"), Optional.of("Nope")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Nope")
        .hasMessageContaining("Shop, ShopWidgets");
  }

  private static List<String> testCommand(
      final List<String> schemes, final Optional<String> scheme) {
    final InitInputs inputs =
        new InitInputs(
            new FakeEnvironmentInspection(),
            root -> PackageManifest.EMPTY,
            root -> false,
            (root, gate) -> Optional.empty(),
            Optional.empty(),
            (root, tool) -> Optional.empty(),
            WorkspaceMembers.none(),
            new SwiftPorts(xcode(schemes), (root, name) -> Optional.empty(), root -> List.of()));
    final Project project = XCODE.withXcodeScheme(scheme.orElse(null));
    final List<InitGateOption> gates =
        InitToolchains.support(project).gates(project, QualityPreset.STANDARD, inputs).analysis();
    return gates.stream()
        .filter(gate -> "xcodebuild-test".equals(gate.id()))
        .findFirst()
        .orElseThrow()
        .command();
  }

  private static XcodeSchemes xcode(final List<String> schemes) {
    return new XcodeSchemes() {
      @Override
      public Optional<String> workspace(final Path root) {
        return Optional.empty();
      }

      @Override
      public List<String> schemes(final Path root) {
        return schemes;
      }

      @Override
      public boolean macOsOnly(final Path root) {
        return false;
      }
    };
  }
}
