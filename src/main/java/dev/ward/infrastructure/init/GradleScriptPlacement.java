package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.GradleKotlinModules;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Places the module-placed gates in each Kotlin module script and declares their versioned plugins
 * in the root script. Every other gate stays with the root edit, as {@link GradleToolConfigurator}
 * configures it.
 */
final class GradleScriptPlacement {
  private static final Logger LOGGER = Logger.getLogger(GradleScriptPlacement.class.getName());

  private GradleScriptPlacement() {}

  /**
   * The root's plugin declarations: its own gates in full, and the versioned plugins of
   * module-placed gates with {@code apply false}. A plugin the root already declares is left alone.
   */
  static List<String> rootDeclarations(
      final GradleBuildFileEdit root,
      final List<GradlePlugin> plugins,
      final Set<String> rootGates,
      final Set<String> moduleGates,
      final GradleToolVersions versions) {
    final boolean kotlin = root.buildFile().toString().endsWith(".kts");
    final List<String> declarations = new ArrayList<>();
    for (final GradlePlugin plugin : plugins) {
      if (root.contains(plugin.id())) {
        continue;
      }
      if (rootGates.contains(plugin.gate())) {
        final String version = plugin.versioned() ? versions.version(plugin.id()) : "";
        declarations.add(plugin.declaration(kotlin, version, false));
        root.record(plugin.id(), version);
      } else if (moduleGates.contains(plugin.gate()) && plugin.versioned()) {
        final String version = versions.version(plugin.id());
        declarations.add(plugin.declaration(kotlin, version, true));
        root.record(plugin.id(), version);
      }
    }
    return declarations;
  }

  /** The edit of one Kotlin module script with the module-placed plugins and blocks added. */
  static GradleBuildFileEdit moduleEdit(
      final Path module,
      final List<GradlePlugin> plugins,
      final Set<String> moduleGates,
      final QualityPreset preset,
      final GradleToolVersions versions) {
    final Path script =
        GradleKotlinModules.scriptFile(module)
            .orElseThrow(
                () ->
                    new WardException("Could not configure Gradle tools: a module has no script."));
    final GradleBuildFileEdit edit = GradleBuildFileEdit.read(script);
    final boolean kotlin = script.toString().endsWith(".kts");
    final List<String> declarations = new ArrayList<>();
    for (final GradlePlugin plugin : plugins) {
      if (moduleGates.contains(plugin.gate()) && !edit.contains(plugin.id())) {
        declarations.add(plugin.declaration(kotlin, "", false));
        edit.record(plugin.id(), "");
      }
    }
    edit.addPlugins(declarations);
    GradleBuildBlocks.append(edit, moduleGates, preset, versions, true);
    return edit;
  }

  /** The selected gates that go into module scripts; none without Kotlin modules. */
  static Set<String> moduleGates(final Set<String> selected, final List<Path> modules) {
    return modules.isEmpty()
        ? Set.of()
        : selected.stream().filter(GradleScriptPlacement::modulePlaced).collect(Collectors.toSet());
  }

  /** Returns whether any selected gate goes into module scripts. */
  static boolean hasModuleGate(final Set<String> selected) {
    return selected.stream().anyMatch(GradleScriptPlacement::modulePlaced);
  }

  /** Logs the placement decision with counts only: no paths or script content. */
  static void logPlacement(
      final Project project, final List<Path> kotlinModules, final boolean rootJvm) {
    if (kotlinModules.isEmpty()) {
      if (!rootJvm && GradleKotlinModules.hasKotlinSources(project.root())) {
        LOGGER.log(Level.INFO, "event=gradle-kotlin-module-missing");
      }
      return;
    }
    LOGGER.log(
        Level.INFO,
        () ->
            "event=gradle-module-placement modules="
                + kotlinModules.size()
                + " root="
                + (rootJvm ? "yes" : "no"));
  }

  private static boolean modulePlaced(final String gate) {
    return GradleBuildBlocks.moduleGates().contains(gate);
  }
}
