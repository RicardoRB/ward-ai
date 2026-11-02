package dev.ward.infrastructure.init.maven;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.QualityPreset;
import dev.ward.infrastructure.init.maven.PomTools.Tool;
import java.util.List;

/**
 * Places a module-placed tool in a multi-module build: the aggregator pins its version in {@code
 * pluginManagement}, and each Java module that lacks it declares it in its own {@code
 * build/plugins}.
 */
final class PomModulePlacement {
  private PomModulePlacement() {}

  /**
   * Places {@code tool} in the modules of {@code aggregator}. A tool the aggregator already
   * declares in {@code build/plugins} stays there, and no module gets it.
   */
  static void place(
      final Tool tool,
      final QualityPreset preset,
      final PomToolVersions versions,
      final PomEdit aggregator) {
    if (PomXml.hasProjectPlugin(aggregator.content, tool.artifact())) {
      aggregator.warnings.add(
          tool.artifact()
              + " is declared in the aggregator's build/plugins, so "
              + tool.gate()
              + " is not placed in modules.");
      return;
    }
    final CompatibilityFinding finding = versions.plugin(tool);
    final String selectedVersion = finding == null ? null : finding.selectedVersion();
    final String engineVersion = finding == null ? null : versions.engine(tool);
    final List<PomEdit> needing =
        aggregator.modules.stream()
            .filter(module -> !PomTools.declares(module.content, tool.artifact()))
            .toList();
    if (needing.isEmpty()) {
      return;
    }
    if (!PomTools.declares(aggregator.content, tool.artifact())) {
      aggregator.addManaged(tool, preset, selectedVersion);
    }
    needing.forEach(module -> module.addModule(tool, preset, selectedVersion, engineVersion));
  }
}
