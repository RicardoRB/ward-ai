package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The opt-in crap gate: offered outside every preset, ordered after its coverage data gate. */
class InitCrapOfferTest {
  private static final String CRAP = "crap";
  private static final Path ROOT = Path.of("/shop");
  private final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

  @ParameterizedTest
  @EnumSource(
      value = BuildTool.class,
      names = {"MAVEN", "GRADLE"})
  void jvmOffersTheGateButNoPresetSelectsIt(final BuildTool tool) {
    final Project project = new Project(ROOT, Language.JAVA, tool, Framework.NONE);

    assertThat(catalog.gates(project, null)).extracting(InitGateOption::id).contains(CRAP);
    for (final QualityPreset preset : QualityPreset.values()) {
      assertThat(catalog.presetGates(project, preset)).doesNotContain(CRAP);
    }
    assertThat(
            ConfigurationTemplate.render(
                resolve(project, QualityPreset.STRICT, presetGates(project, QualityPreset.STRICT)),
                QualityPreset.STRICT,
                project))
        .doesNotContain("crap");
  }

  @Test
  void aPresetPlusTheGateWritesAnUsesOnlyTableAfterCoverage() {
    final Project project = new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final List<String> ids = new ArrayList<>(catalog.presetGates(project, QualityPreset.STANDARD));
    ids.add(CRAP);

    final List<InitGateOption> gates = resolve(project, QualityPreset.STANDARD, ids);
    final String toml = ConfigurationTemplate.render(gates, QualityPreset.STANDARD, project);

    assertThat(gates.stream().map(InitGateOption::id).toList())
        .containsSubsequence("coverage", CRAP);
    assertThat(toml)
        .contains(
            "[checks.crap]\nenabled = true\nprofiles = [\"pre-push\", \"ci\"]\nuses = \"crap\"\n")
        .doesNotContain("[checks.crap.paths]");
    assertThat(toml.substring(toml.indexOf("[checks.crap]"))).doesNotContain("command =");
  }

  @Test
  void theGateNeedsCoverageAndTheErrorNamesBoth() {
    final Project project = new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE);

    assertThatThrownBy(() -> resolve(project, null, List.of("compile", CRAP)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("crap")
        .hasMessageContaining("'coverage'");
  }

  @Test
  void theGateIsPlacedAfterCoverageWhateverTheSelectionOrder() {
    final Project project = new Project(ROOT, Language.JAVA, BuildTool.GRADLE, Framework.NONE);

    assertThat(resolve(project, null, List.of(CRAP, "coverage", "tests")))
        .extracting(InitGateOption::id)
        .containsSubsequence("coverage", CRAP);
  }

  @ParameterizedTest
  @EnumSource(
      value = BuildTool.class,
      names = {"SWIFTPM", "XCODE"})
  void swiftIsNotOfferedTheGateAndTheErrorSaysHowToAddItByHand(final BuildTool tool) {
    final Project project = new Project(ROOT, Language.SWIFT, tool, Framework.NONE);

    if (tool == BuildTool.SWIFTPM) {
      assertThat(catalog.gates(project, null)).extracting(InitGateOption::id).doesNotContain(CRAP);
    }
    assertThatThrownBy(() -> catalog.gate(project, CRAP))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("not available for this project")
        .hasMessageContaining("report path");
  }

  @Test
  void androidIsNotOfferedTheGate() {
    final Project project = new Project(ROOT, Language.KOTLIN, BuildTool.GRADLE, Framework.ANDROID);

    assertThat(catalog.gates(project, null)).extracting(InitGateOption::id).doesNotContain(CRAP);
    assertThatThrownBy(() -> catalog.gate(project, CRAP)).isInstanceOf(WardException.class);
  }

  private List<String> presetGates(final Project project, final QualityPreset preset) {
    return catalog.presetGates(project, preset);
  }

  private List<InitGateOption> resolve(
      final Project project, final QualityPreset preset, final List<String> ids) {
    return SelectedGates.resolve(
        catalog, project, new InitSelection(List.of("none"), ids, null, preset), "openspec");
  }
}
