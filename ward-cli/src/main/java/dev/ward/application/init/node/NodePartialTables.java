package dev.ward.application.init.node;

import dev.ward.application.init.PartialCheckDefaults.Table;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/** The partial-run tables of the npm, pnpm, Yarn and Bun lint and format gates. */
final class NodePartialTables {
  private static final List<String> PATHS = List.of("{paths}");
  private static final List<String> NODE_SOURCES =
      List.of(
          "**/*.ts",
          "**/*.tsx",
          "**/*.mts",
          "**/*.cts",
          "**/*.js",
          "**/*.jsx",
          "**/*.mjs",
          "**/*.cjs");
  // The component files of a detected framework, which ESLint reads only when its config does.
  private static final Map<Framework, String> FRAMEWORK_SOURCES =
      Map.of(
          Framework.VUE, "**/*.vue",
          Framework.SVELTE, "**/*.svelte",
          Framework.ASTRO, "**/*.astro");

  // Their full commands end with a target (or none), so the narrowed command replaces it.
  private static final Map<String, BiFunction<Project, QualityPreset, Table>> TABLES =
      Map.of(
          "eslint",
          (project, preset) ->
              new Table(
                  eslintSources(project),
                  List.of("eslint.config.*", ".eslintrc*", "package.json"),
                  PATHS,
                  null,
                  null,
                  NodeTools.command(project.buildTool(), "eslint", "--format", "json")),
          "prettier",
          (project, preset) ->
              new Table(
                  NODE_SOURCES,
                  List.of(".prettierrc*", "prettier.config.*", ".prettierignore", "package.json"),
                  PATHS,
                  null,
                  null,
                  NodeTools.command(project.buildTool(), "prettier", "--check")),
          "biome",
          (project, preset) ->
              new Table(
                  NODE_SOURCES,
                  List.of(
                      "biome.json", "biome.jsonc", ".biome.json", ".biome.jsonc", "package.json"),
                  PATHS,
                  null,
                  null,
                  biomeCommand(project.buildTool(), preset)));

  /** The gates that have a partial table. */
  static final Set<String> GATES = TABLES.keySet();

  private NodePartialTables() {}

  /** Returns the partial-run table of a Node gate, or empty for any other gate. */
  static Optional<Table> forGate(
      final String gate, final Project project, final QualityPreset preset) {
    return Optional.ofNullable(TABLES.get(gate)).map(factory -> factory.apply(project, preset));
  }

  /** The Node sources, plus the component files of a detected framework. */
  private static List<String> eslintSources(final Project project) {
    final String framework = FRAMEWORK_SOURCES.get(project.framework());
    return framework == null
        ? NODE_SOURCES
        : Stream.concat(NODE_SOURCES.stream(), Stream.of(framework)).toList();
  }

  /**
   * The narrowed Biome command. {@code --no-errors-on-unmatched} lets an edited file that Biome
   * ignores pass; strict keeps warnings failing.
   */
  private static List<String> biomeCommand(final BuildTool manager, final QualityPreset preset) {
    final List<String> arguments =
        new ArrayList<>(
            List.of(
                "biome",
                "ci",
                "--colors=off",
                "--reporter=checkstyle",
                "--no-errors-on-unmatched"));
    if (preset == QualityPreset.STRICT) {
      arguments.add("--error-on-warnings");
    }
    return NodeTools.command(manager, arguments.toArray(String[]::new));
  }
}
