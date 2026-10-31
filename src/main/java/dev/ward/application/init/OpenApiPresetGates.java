package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;

/** The OpenAPI gates a preset adds when the project tracks a spec. */
final class OpenApiPresetGates {
  private OpenApiPresetGates() {}

  /**
   * Returns the OpenAPI gates {@code preset} adds for a Git-tracked spec, none without one. The
   * compatibility gate compares with the base branch, so only strict adds it.
   */
  static List<String> of(
      final EnvironmentInspection environment, final Project project, final QualityPreset preset) {
    if (preset == null || environment.openApiSpec(project.root()).isEmpty()) {
      return List.of();
    }
    return preset == QualityPreset.STRICT
        ? List.of(
            SupportedQualityGates.OPENAPI_LINT,
            SupportedQualityGates.OPENAPI_DIFF,
            SupportedQualityGates.OPENAPI_COMPAT)
        : List.of(SupportedQualityGates.OPENAPI_LINT, SupportedQualityGates.OPENAPI_DIFF);
  }
}
