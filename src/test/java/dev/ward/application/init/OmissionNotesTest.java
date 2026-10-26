package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Angular note that the plan and the written output both carry. */
class OmissionNotesTest {
  private static final Path ROOT = Path.of("app");

  @Test
  void angularTypeScriptProjectNotesTheTemplatesAreNotChecked() {
    assertThat(notes(Language.TYPESCRIPT, Framework.ANGULAR))
        .singleElement()
        .satisfies(note -> assertThat(note).contains("Angular templates are not type-checked"));
  }

  @Test
  void otherFrameworksAndJavaScriptHaveNoNote() {
    assertThat(notes(Language.TYPESCRIPT, Framework.VUE)).isEmpty();
    assertThat(notes(Language.JAVASCRIPT, Framework.ANGULAR)).isEmpty();
  }

  private static List<String> notes(final Language language, final Framework framework) {
    final Project project =
        new Project(ROOT, language, BuildTool.NPM, framework, false, false, ROOT, Set.of(), "24");
    final InitGeneralCatalog catalog =
        new InitGeneralCatalog(
            new InitInputs(
                new FakeEnvironmentInspection("npm"),
                directory -> PackageManifest.EMPTY,
                directory -> false));
    return OmissionNotes.of(catalog, project, QualityPreset.STANDARD);
  }
}
