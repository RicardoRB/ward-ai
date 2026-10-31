package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Presets add the OpenAPI gates for a committed spec, and a server framework without one is noted.
 */
class OpenApiPresetGatesTest {
  private static final Project SPRING =
      new Project(Path.of("/shop"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);
  private static final Project PLAIN =
      new Project(Path.of("/tool"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private static final String SPEC = "src/main/resources/openapi.yml";

  @Test
  void standardPresetAddsLintAndDiffForACommittedSpec() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(environment(SPEC));

    assertThat(catalog.presetGates(SPRING, QualityPreset.STANDARD))
        .contains("openapi-lint", "openapi-diff")
        .doesNotContain("openapi-compat");
  }

  @Test
  void strictPresetAlsoAddsCompatibility() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(environment(SPEC));

    assertThat(catalog.presetGates(SPRING, QualityPreset.STRICT))
        .contains("openapi-lint", "openapi-diff", "openapi-compat");
  }

  @Test
  void presetsAddNoOpenApiGateWithoutACommittedSpec() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(environment(null));

    assertThat(catalog.presetGates(SPRING, QualityPreset.STRICT))
        .noneMatch(id -> id.startsWith("openapi"));
  }

  @Test
  void serverFrameworkWithoutSpecGetsANoteNamingTheSpecFiles() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(environment(null));

    assertThat(OmissionNotes.of(catalog, SPRING, QualityPreset.STANDARD))
        .anySatisfy(
            note ->
                assertThat(note)
                    .contains("Spring Boot project serves HTTP", "openapi.yml", "openapi.json"));
  }

  @Test
  void serverFrameworkWithASpecGetsNoNote() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(environment(SPEC));

    assertThat(OmissionNotes.of(catalog, SPRING, QualityPreset.STANDARD))
        .noneMatch(note -> note.contains("serves HTTP"));
  }

  @Test
  void projectWithoutServerFrameworkGetsNoNote() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(environment(null));

    assertThat(OmissionNotes.of(catalog, PLAIN, QualityPreset.STANDARD))
        .noneMatch(note -> note.contains("serves HTTP"));
  }

  private static EnvironmentInspection environment(final String spec) {
    return new EnvironmentInspection() {
      @Override
      public EnvironmentFacts inspect(final Path projectRoot) {
        return new EnvironmentFacts(Set.of(), Map.of());
      }

      @Override
      public Optional<String> openApiSpec(final Path projectRoot) {
        return Optional.ofNullable(spec);
      }
    };
  }
}
