package dev.ward.application.init;

import dev.ward.domain.init.GateOmission;
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
  private static final String ANGULAR_TEMPLATES =
      "Angular templates are not type-checked: tsc reads only .ts files, so the type-check gate"
          + " misses them.";

  private OmissionNotes() {}

  /** Returns the notes for {@code project}, and for the gates {@code preset} omits, if any. */
  static List<String> of(
      final InitGeneralCatalog catalog, final Project project, final QualityPreset preset) {
    final List<String> notes = new ArrayList<>();
    if (project.language() == Language.TYPESCRIPT && project.framework() == Framework.ANGULAR) {
      notes.add(ANGULAR_TEMPLATES);
    }
    if (preset != null) {
      JdkGates.of(catalog).presetOmissions(project, preset).stream()
          .map(GateOmission::note)
          .forEach(notes::add);
    }
    return List.copyOf(notes);
  }
}
