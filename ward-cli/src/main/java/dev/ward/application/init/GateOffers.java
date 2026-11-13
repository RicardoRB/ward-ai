package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;

/** Explains why init does not offer a supported gate for a project. */
final class GateOffers {
  private GateOffers() {}

  /**
   * Checks that init offers gate {@code id} for {@code project}.
   *
   * @throws WardException if the project lacks what the gate needs
   */
  static void require(
      final EnvironmentInspection environment, final Project project, final String id) {
    requireApiBaseline(environment, project, id);
    requireOpenApiSpec(environment, project, id);
  }

  private static void requireApiBaseline(
      final EnvironmentInspection environment, final Project project, final String id) {
    if (!SupportedQualityGates.API_COMPAT.equals(id)) {
      return;
    }
    if (!environment.hasReleaseTag(project.root())) {
      throw new WardException(
          "api-compat compares sources with the latest release tag, so it is offered only when"
              + " a tag such as v1.2.3 is reachable from HEAD. Fetch tags or create one.");
    }
  }

  private static void requireOpenApiSpec(
      final EnvironmentInspection environment, final Project project, final String id) {
    if (SupportedQualityGates.openApiIds().contains(id)
        && environment.openApiSpec(project.root()).isEmpty()) {
      throw new WardException(
          id
              + " checks a committed OpenAPI spec, so it is offered only when Git tracks"
              + " openapi.yml, openapi.yaml, or openapi.json in src/main/resources or the project"
              + " root."
              + (project.framework() == Framework.FASTAPI
                  ? fastApiExport(environment, project)
                  : ""));
    }
  }

  /**
   * Returns the sentence that names how to export a FastAPI app's document: {@code app.openapi()}
   * is the document the app serves at {@code /openapi.json}. Ward does not run it.
   */
  private static String fastApiExport(
      final EnvironmentInspection environment, final Project project) {
    final String target = environment.fastApiApp(project.root()).orElse("<module>:<app>");
    final String module = target.substring(0, target.indexOf(':'));
    final String attribute = target.substring(target.indexOf(':') + 1);
    return " For a FastAPI app, export its document to openapi.json with: python -c \"import json;"
        + " from "
        + module
        + " import "
        + attribute
        + "; print(json.dumps("
        + attribute
        + ".openapi()))\" > openapi.json";
  }
}
