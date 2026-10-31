package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The notes that the plan and the written output both carry for a framework. */
class OmissionNotesTest {
  private static final Path ROOT = Path.of("app");

  @Test
  void angularStrictNotesCoverageAndMutationTesting() {
    assertThat(notes(Language.TYPESCRIPT, Framework.ANGULAR, QualityPreset.STRICT, false))
        .hasSize(2)
        .anySatisfy(note -> assertThat(note).contains("Coverage minimums are not enforced"))
        .anySatisfy(note -> assertThat(note).contains("Mutation testing is not offered"));
  }

  @Test
  void angularStandardNotesOnlyThatCoverageIsNotEnforced() {
    assertThat(notes(Language.TYPESCRIPT, Framework.ANGULAR, QualityPreset.STANDARD, false))
        .singleElement()
        .satisfies(note -> assertThat(note).contains("angular.json"));
  }

  @Test
  void angularWithCoverageThresholdsHasNoCoverageNote() {
    assertThat(notes(Language.TYPESCRIPT, Framework.ANGULAR, QualityPreset.STANDARD, true))
        .isEmpty();
  }

  @Test
  void otherFrameworksAndJavaScriptHaveNoNote() {
    assertThat(notes(Language.TYPESCRIPT, Framework.VUE, QualityPreset.STRICT, false)).isEmpty();
    assertThat(notes(Language.JAVASCRIPT, Framework.ANGULAR, QualityPreset.STRICT, false))
        .isEmpty();
  }

  private static List<String> notes(
      final Language language,
      final Framework framework,
      final QualityPreset preset,
      final boolean thresholdsSet) {
    final Project project =
        new Project(
            ROOT,
            language,
            BuildTool.NPM,
            framework,
            false,
            false,
            ROOT,
            Set.of(),
            "24",
            TaskRunner.NONE,
            null);
    final InitGeneralCatalog catalog =
        new InitGeneralCatalog(
            new InitInputs(
                new FakeEnvironmentInspection("npm"),
                directory -> PackageManifest.EMPTY,
                directory -> false,
                new ThresholdConfigs(thresholdsSet)));
    return OmissionNotes.of(catalog, project, preset);
  }

  /** Node tool configs whose only answer is whether Angular's coverage thresholds are set. */
  private record ThresholdConfigs(boolean thresholdsSet) implements NodeToolConfigs {
    @Override
    public Optional<Path> userConfig(final Path root, final String gate) {
      return Optional.empty();
    }

    @Override
    public boolean angularCoverageThresholdsSet(final Path root) {
      return thresholdsSet;
    }
  }
}
