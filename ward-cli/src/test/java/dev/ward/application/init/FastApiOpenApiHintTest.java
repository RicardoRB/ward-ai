package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A FastAPI project without a committed spec is told how to export its OpenAPI document. */
class FastApiOpenApiHintTest {
  private static final Project FASTAPI =
      new Project(Path.of("/shop"), Language.PYTHON, BuildTool.UV, Framework.FASTAPI);

  @Test
  void namesTheAppModuleAndTheExportCommand() {
    assertThatThrownBy(
            () -> GateOffers.require(environment("app.main:app"), FASTAPI, "openapi-lint"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("from app.main import app")
        .hasMessageContaining("app.openapi()")
        .hasMessageContaining("> openapi.json");
  }

  @Test
  void stillExplainsTheCommittedSpecWhenTheAppIsNotFound() {
    assertThatThrownBy(() -> GateOffers.require(environment(null), FASTAPI, "openapi-lint"))
        .hasMessageContaining("from <module> import <app>");
  }

  @Test
  void aNonFastApiProjectGetsNoExportHint() {
    final Project flask =
        new Project(Path.of("/shop"), Language.PYTHON, BuildTool.UV, Framework.FLASK);

    assertThatThrownBy(() -> GateOffers.require(environment("app.main:app"), flask, "openapi-lint"))
        .satisfies(error -> assertThat(error.getMessage()).doesNotContain("openapi()"));
  }

  private static EnvironmentInspection environment(final String app) {
    return new EnvironmentInspection() {
      @Override
      public EnvironmentFacts inspect(final Path projectRoot) {
        return new EnvironmentFacts(java.util.Set.of(), java.util.Map.of());
      }

      @Override
      public Optional<String> openApiSpec(final Path projectRoot) {
        return Optional.empty();
      }

      @Override
      public Optional<String> fastApiApp(final Path projectRoot) {
        return Optional.ofNullable(app);
      }
    };
  }
}
