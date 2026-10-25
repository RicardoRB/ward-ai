package dev.ward.application.init;

import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;

/** The plan and output notes for preset gates the project's runtime JDK cannot run. */
final class OmissionNotes {
  private OmissionNotes() {}

  /** Returns one note per gate {@code preset} enables but the catalog omits; none without one. */
  static List<String> of(
      final InitGeneralCatalog catalog, final Project project, final QualityPreset preset) {
    return preset == null
        ? List.of()
        : JdkGates.of(catalog).presetOmissions(project, preset).stream()
            .map(GateOmission::note)
            .toList();
  }
}
