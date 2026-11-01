package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;

/**
 * The plan and output notes: preset gates the runtime JDK cannot run, and what a framework's type
 * check does not cover.
 */
final class OmissionNotes {
  private static final String ANGULAR_NO_COVERAGE =
      "Coverage minimums are not enforced for this Angular project: angular.json sets no"
          + " coverageThresholds, so ng-test checks no coverage. Set coverageThresholds under the"
          + " test target's options in angular.json (or in karma.conf.js for Karma).";
  private static final String ANGULAR_NO_MUTATION =
      "Mutation testing is not offered for Angular: no Stryker runner works with Angular's test"
          + " builder, so the strict preset omits stryker.";

  private static final String NO_SPEC_NOTE =
      " Git tracks no OpenAPI spec, so openapi-lint and openapi-diff are not offered. Commit"
          + " openapi.yml or openapi.json under src/main/resources or the project root, then run"
          + " init again to add them.";

  private OmissionNotes() {}

  /** Returns the notes for {@code project}, and for the gates {@code preset} omits, if any. */
  static List<String> of(
      final InitGeneralCatalog catalog, final Project project, final QualityPreset preset) {
    final List<String> notes = new ArrayList<>();
    final EnvironmentInspection environment = catalog.inputs.environment();
    if (project.framework().servesHttp() && environment.openApiSpec(project.root()).isEmpty()) {
      notes.add(
          "The " + project.framework().displayName() + " project serves HTTP." + NO_SPEC_NOTE);
    }
    if (project.language() == Language.TYPESCRIPT && project.framework() == Framework.ANGULAR) {
      if (!catalog.inputs.configs().angularCoverageThresholdsSet(project.root())) {
        notes.add(ANGULAR_NO_COVERAGE);
      }
      if (preset == QualityPreset.STRICT) {
        notes.add(ANGULAR_NO_MUTATION);
      }
    }
    InitToolchains.find(project)
        .map(support -> support.notes(project, preset, catalog.inputs))
        .ifPresent(notes::addAll);
    return List.copyOf(notes);
  }
}
