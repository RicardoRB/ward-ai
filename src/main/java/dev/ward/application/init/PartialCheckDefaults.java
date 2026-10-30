package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * The {@code [checks.<id>.paths]} table init writes for a gate whose tool can be scoped to edited
 * files. Each narrowing form was verified against the real tool: a form the tool does not
 * understand analyzes nothing and passes.
 */
final class PartialCheckDefaults {
  private static final List<String> JAVA_SOURCES = List.of("src/**/*.java");
  private static final List<String> MAVEN_BUILD = List.of("pom.xml");
  private static final List<String> GRADLE_BUILD = List.of("build.gradle*", "settings.gradle*");
  private static final String GRADLE_PATHS = "-PwardPaths={paths}";
  private static final String COMMA = ",";
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
  private static final List<String> PATHS = List.of("{paths}");
  // The component files of a detected framework, which ESLint reads only when its config does.
  private static final Map<Framework, String> FRAMEWORK_SOURCES =
      Map.of(
          Framework.VUE, "**/*.vue",
          Framework.SVELTE, "**/*.svelte",
          Framework.ASTRO, "**/*.astro");

  // Their full commands end with a target (or none), so the narrowed command replaces it.
  private static final Map<String, BiFunction<Project, QualityPreset, Table>> NODE_TABLES =
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

  private static final List<String> PYTHON_SOURCES = List.of("**/*.py", "**/*.pyi");

  // Their commands end with the target, so the narrowed command replaces it, as for Node.
  private static final Map<String, BiFunction<Project, QualityPreset, Table>> PYTHON_TABLES =
      Map.of(
          "ruff-format",
          (project, preset) ->
              new Table(
                  PYTHON_SOURCES,
                  List.of("ruff.toml", ".ruff.toml", "pyproject.toml"),
                  PATHS,
                  null,
                  null,
                  PythonTools.command(project.buildTool(), "ruff", "format", "--check")),
          "ruff",
          (project, preset) ->
              new Table(
                  PYTHON_SOURCES,
                  List.of("ruff.toml", ".ruff.toml", "pyproject.toml"),
                  PATHS,
                  null,
                  null,
                  PythonTools.command(
                      project.buildTool(), "ruff", "check", "--output-format", "json")));

  private static final Map<String, TableFactory> TABLES =
      Map.of(
          "checkstyle",
          PartialCheckDefaults::checkstyle,
          "pmd",
          PartialCheckDefaults::pmd,
          "format",
          (gradle, preset, build) -> format(gradle, build),
          "semgrep",
          (gradle, preset, build) ->
              new Table(List.of("**"), List.of(".semgrep*"), List.of("{paths}"), null, null),
          // Its history scan misses uncommitted and staged edits, so edited files are scanned
          // directly.
          "gitleaks",
          (gradle, preset, build) ->
              new Table(
                  List.of("**"),
                  List.of(".gitleaks*"),
                  List.of("--no-git", "--source={paths}"),
                  null,
                  null));

  /**
   * The table's keys; {@code item} and {@code separator} are omitted when {@code null}, and {@code
   * command} when empty.
   */
  record Table(
      List<String> match,
      List<String> full,
      List<String> args,
      String item,
      String separator,
      List<String> command) {
    Table {
      match = List.copyOf(match);
      full = List.copyOf(full);
      args = List.copyOf(args);
      command = List.copyOf(command);
    }

    Table(
        final List<String> match,
        final List<String> full,
        final List<String> args,
        final String item,
        final String separator) {
      this(match, full, args, item, separator, List.of());
    }

    private static Table whole(final List<String> match, final List<String> full) {
      return new Table(match, full, List.of(), null, null);
    }
  }

  /** Builds a gate's table for Maven or Gradle, with or without a preset. */
  @FunctionalInterface
  private interface TableFactory {
    Table create(boolean gradle, QualityPreset preset, List<String> build);
  }

  private PartialCheckDefaults() {}

  static Optional<Table> forGate(
      final String gate, final Project project, final QualityPreset preset) {
    final Optional<Table> turbo = TurboPartialDefaults.forGate(gate, project);
    if (turbo.isPresent()) {
      return turbo;
    }
    final boolean gradle = project.buildTool() == BuildTool.GRADLE;
    final List<String> build = gradle ? GRADLE_BUILD : MAVEN_BUILD;
    return Optional.ofNullable(NODE_TABLES.get(gate))
        .map(factory -> factory.apply(project, preset))
        .or(
            () ->
                Optional.ofNullable(PYTHON_TABLES.get(gate))
                    .map(factory -> factory.apply(project, preset)))
        .or(
            () ->
                Optional.ofNullable(TABLES.get(gate))
                    .map(factory -> factory.create(gradle, preset, build)));
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

  /** Returns the gates that have a partial table, for the gate contract test. */
  static Set<String> gates() {
    final Set<String> gates = new HashSet<>(TABLES.keySet());
    gates.addAll(NODE_TABLES.keySet());
    return gates;
  }

  /**
   * The OpenAPI lint and diff gates always analyze the whole spec, so editing it runs them in full
   * and other edits skip them.
   */
  static Optional<Table> forOpenApi(final String gate, final String spec) {
    if (spec == null
        || !SupportedQualityGates.OPENAPI_LINT.equals(gate)
            && !SupportedQualityGates.OPENAPI_DIFF.equals(gate)) {
      return Optional.empty();
    }
    return Optional.of(Table.whole(List.of(spec), List.of()));
  }

  /** Checkstyle's {@code includes} matches source-relative paths, so only the file name works. */
  private static Table checkstyle(
      final boolean gradle, final QualityPreset preset, final List<String> build) {
    final List<String> config = withRuleFile(preset, "config/checkstyle.xml", build);
    if (gradle) {
      return gradleFiltered(preset, config);
    }
    return new Table(
        JAVA_SOURCES, config, List.of("-Dcheckstyle.includes={paths}"), "**/{name}", COMMA);
  }

  /** The Maven PMD plugin has no property for {@code includes}; it runs in full, cached. */
  private static Table pmd(
      final boolean gradle, final QualityPreset preset, final List<String> build) {
    final List<String> config = withRuleFile(preset, "config/pmd-ruleset.xml", build);
    return gradle ? gradleFiltered(preset, config) : Table.whole(JAVA_SOURCES, config);
  }

  /**
   * Spotless matches {@code spotlessFiles} against absolute paths. Gradle's single-file mode
   * applies formatting instead of checking it, so Gradle runs in full.
   */
  private static Table format(final boolean gradle, final List<String> build) {
    final List<String> config = gradle ? List.of(build.getFirst()) : build;
    if (gradle) {
      return Table.whole(JAVA_SOURCES, config);
    }
    return new Table(JAVA_SOURCES, config, List.of("-DspotlessFiles={paths}"), ".*/{path}", COMMA);
  }

  /** Only the Gradle blocks init writes with a preset read {@code wardPaths}. */
  private static Table gradleFiltered(final QualityPreset preset, final List<String> config) {
    if (preset == null) {
      return Table.whole(JAVA_SOURCES, config);
    }
    return new Table(JAVA_SOURCES, config, List.of(GRADLE_PATHS), "{path}", COMMA);
  }

  private static List<String> withRuleFile(
      final QualityPreset preset, final String ruleFile, final List<String> build) {
    final List<String> config = new ArrayList<>(build.size() + 1);
    if (preset != null) {
      config.add(ruleFile);
    }
    config.addAll(build);
    return config;
  }
}
