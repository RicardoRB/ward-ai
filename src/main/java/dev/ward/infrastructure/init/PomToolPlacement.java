package dev.ward.infrastructure.init;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.QualityPreset;
import dev.ward.infrastructure.init.PomTools.Tool;
import java.util.function.Predicate;

/** Places a tool in the aggregator's own pom, or updates the version it already declares there. */
final class PomToolPlacement {
  private PomToolPlacement() {}

  static void apply(
      final Tool tool,
      final QualityPreset preset,
      final PomToolVersions versions,
      final Predicate<? super CompatibilityFinding> approval,
      final PomEdit edit) {
    final CompatibilityFinding finding = versions.plugin(tool);
    if (finding != null && !PomTools.declares(edit.content, tool.artifact())) {
      edit.add(tool, preset, finding.selectedVersion(), versions.engine(tool));
    } else if (finding != null && finding.changesVersion()) {
      final boolean accepted = approval.test(finding);
      if (accepted) {
        edit.updateVersion(tool.artifact(), finding);
      } else {
        edit.warnings.add(
            "Compatibility update declined for "
                + tool.artifact()
                + "; selected gate was not reconfigured.");
      }
    } else if (PomTools.declares(edit.content, tool.artifact())) {
      if (preset != null && tool.presetSnippet() != null) {
        edit.warnings.add(
            tool.artifact() + " already declared; " + preset.id() + " settings not applied");
      }
    } else {
      edit.add(tool, preset, null, null);
    }
  }
}
