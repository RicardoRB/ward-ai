package dev.ward.infrastructure.init.maven;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.ScriptEdit;
import dev.ward.infrastructure.init.maven.PomTools.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A pom being edited in memory, with what changed so far. The aggregator's edit holds its module
 * edits, so one run writes, restores and reports every pom it touches.
 */
final class PomEdit {
  /** Rule files sit in the aggregator, so a module's declaration resolves them from there. */
  private static final String PROJECT_BASEDIR = "${project.basedir}";

  private static final String MULTI_MODULE_DIRECTORY = "${maven.multiModuleProjectDirectory}";

  final Path pom;
  final String original;
  final List<String> tools = new ArrayList<>();
  final List<String> warnings = new ArrayList<>();
  final List<String> details = new ArrayList<>();
  final List<PomEdit> modules = new ArrayList<>();
  String content;

  PomEdit(final Path pom, final String original) {
    this.pom = pom;
    this.original = original;
    this.content = original;
  }

  /** Reads {@code pom} into an edit; a read failure names the file. */
  static PomEdit read(final Path pom) {
    try {
      return new PomEdit(pom, Files.readString(pom));
    } catch (IOException e) {
      throw new WardException(
          "Could not configure Maven tools in " + pom + ": " + e.getMessage(), e);
    }
  }

  /** This pom and then each module pom, in the order they are written. */
  List<PomEdit> files() {
    final List<PomEdit> files = new ArrayList<>(List.of(this));
    files.addAll(modules);
    return files;
  }

  /** Every tool this run added, in the aggregator and in the modules. */
  List<String> allTools() {
    return files().stream().flatMap(edit -> edit.tools.stream()).distinct().toList();
  }

  /** The module poms this run changed, restored on rollback. */
  List<ScriptEdit> moduleScripts() {
    return modules.stream()
        .filter(module -> !module.tools.isEmpty())
        .map(module -> new ScriptEdit(module.pom, module.original))
        .toList();
  }

  void add(
      final Tool tool,
      final QualityPreset preset,
      final String selectedVersion,
      final String engineVersion) {
    content = tool.addTo(content, preset, selectedVersion, engineVersion);
    record(tool, preset, selectedVersion);
  }

  /**
   * Declares a module-placed tool in this module pom. Its rule files sit in the aggregator, so
   * their paths resolve from the multi-module directory instead of the module's own basedir.
   */
  void addModule(
      final Tool tool,
      final QualityPreset preset,
      final String selectedVersion,
      final String engineVersion) {
    final String declaration =
        tool.declaration(preset, selectedVersion, engineVersion)
            .replace(PROJECT_BASEDIR, MULTI_MODULE_DIRECTORY);
    content = PomXml.addPlugin(content, declaration);
    record(tool, preset, selectedVersion);
  }

  void addManaged(final Tool tool, final QualityPreset preset, final String selectedVersion) {
    content = PomXml.addManagedPlugin(content, tool.managed(preset, selectedVersion));
    tools.add(tool.artifact());
    details.add(
        "+ "
            + tool.artifact()
            + " "
            + tool.version(preset, selectedVersion)
            + " (pluginManagement)");
  }

  void updateVersion(final String artifact, final CompatibilityFinding finding) {
    final Optional<String> updated =
        PomXml.replacePluginVersion(
            content, artifact, finding.currentVersion(), finding.selectedVersion());
    if (updated.isEmpty()) {
      warnings.add(
          "Compatibility update skipped for "
              + artifact
              + " because it is not declared in the project-level build; selected gate was not"
              + " reconfigured.");
      return;
    }
    final String change = finding.currentVersion() + " -> " + finding.selectedVersion();
    content = updated.get();
    tools.add(artifact + " " + change);
    details.add("~ " + artifact + " " + change);
  }

  private void record(final Tool tool, final QualityPreset preset, final String selectedVersion) {
    tools.add(selectedVersion == null ? tool.artifact() : tool.artifact() + " " + selectedVersion);
    details.add("+ " + tool.artifact() + " " + tool.version(preset, selectedVersion));
  }
}
