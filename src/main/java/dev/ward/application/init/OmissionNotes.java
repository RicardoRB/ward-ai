package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
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

  private static final String MULTI_MODULE_NO_ARCHUNIT =
      "archunit is not offered for this Gradle build: its modules already enforce the dependency"
          + " direction between them.";

  private static final String SKIPPED_MODULES =
      "These modules are left out of the gates that init places per module, because they have no"
          + " Java sources or apply no Java or Kotlin JVM plugin: ";

  private static final String NO_SPEC_NOTE =
      " Git tracks no OpenAPI spec, so openapi-lint and openapi-diff are not offered. Commit"
          + " openapi.yml or openapi.json under src/main/resources or the project root, then run"
          + " init again to add them.";

  private OmissionNotes() {}

  /** The ArchUnit note for a multi-module Kotlin build under the strict preset, when it applies. */
  private static List<String> multiModuleNotes(
      final Project project, final QualityPreset preset, final EnvironmentInspection environment) {
    return preset == QualityPreset.STRICT && JvmInitSupport.multiModuleKotlin(project, environment)
        ? List.of(MULTI_MODULE_NO_ARCHUNIT)
        : List.of();
  }

  /** The note naming each module of a Maven or Gradle build that init leaves out, if any. */
  private static List<String> skippedModuleNotes(
      final Project project, final EnvironmentInspection environment) {
    if (project.buildTool() != BuildTool.MAVEN && project.buildTool() != BuildTool.GRADLE) {
      return List.of();
    }
    final List<String> skipped = environment.skippedJvmModules(project.root());
    return skipped.isEmpty()
        ? List.of()
        : List.of(SKIPPED_MODULES + String.join(", ", skipped) + ".");
  }

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
    notes.addAll(multiModuleNotes(project, preset, environment));
    notes.addAll(skippedModuleNotes(project, environment));
    if (preset != null) {
      JdkGates.of(catalog).presetOmissions(project, preset).stream()
          .map(GateOmission::note)
          .forEach(notes::add);
    }
    return List.copyOf(notes);
  }
}
