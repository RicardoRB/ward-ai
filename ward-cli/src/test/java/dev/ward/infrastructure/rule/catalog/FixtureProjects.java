package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The projects that the catalog fixtures run in: manifests, other sources, and graph companions.
 */
final class FixtureProjects {

  private static final String PASSING = "passing";

  private FixtureProjects() {}

  /**
   * The findings of a graph rule on {@code file}, in a project of the file and the companion
   * modules named {@code violating_*.py} beside it. Only the findings in {@code file} are compared.
   */
  static List<AnalysisFinding> projectFindings(
      final RuleFixtureRunner runner, final RuleDefinition rule, final Path file)
      throws IOException {
    final List<SourceUnit> units = new ArrayList<>();
    units.add(
        new SourceUnit(
            file.getFileName().toString(), AnalysisLanguage.PYTHON, Files.readString(file)));
    try (Stream<Path> siblings = Files.list(file.getParent())) {
      for (final Path companion :
          siblings
              .filter(path -> path.getFileName().toString().startsWith("violating_"))
              .toList()) {
        units.add(
            new SourceUnit(
                companion.getFileName().toString(),
                AnalysisLanguage.PYTHON,
                Files.readString(companion)));
      }
    }
    return GraphFixtureRunner.run(runner.frontends(), rule, units).stream()
        .filter(finding -> finding.file().equals(file.getFileName().toString()))
        .toList();
  }

  /**
   * The project of a non-graph fixture: its manifests and the other sources, in the rule's
   * language.
   */
  static FixtureProject fixtureProject(final Path file, final RuleDefinition rule)
      throws IOException {
    return new FixtureProject(
        manifests(file), otherSources(file, rule.languages().iterator().next()));
  }

  /** The project folder of a case: {@code passing-project} for a passing case that has one. */
  static Path projectFolder(final Path file) {
    final Path own = file.resolveSibling("passing-project");
    final boolean passing =
        file.getFileName().toString().startsWith(PASSING) && Files.isDirectory(own);
    return passing ? own : file.resolveSibling("project");
  }

  /**
   * The other source files of a fixture's project, in the language of the rule, by their path in
   * the project folder. They are read for cross-file facts only.
   */
  static List<SourceUnit> otherSources(final Path file, final AnalysisLanguage language)
      throws IOException {
    final Path project = projectFolder(file);
    if (!Files.isDirectory(project)) {
      return List.of();
    }
    final List<SourceUnit> units = new ArrayList<>();
    try (Stream<Path> files = Files.walk(project)) {
      for (final Path source : files.filter(Files::isRegularFile).toList()) {
        final String name = source.getFileName().toString();
        if (FileSystemSourceUnits.language(name) == language) {
          final String path = project.relativize(source).toString().replace('\\', '/');
          units.add(new SourceUnit(path, language, Files.readString(source)));
        }
      }
    }
    return units;
  }

  /**
   * The manifests of a fixture's project: the package.json and pnpm-workspace.yaml files under the
   * {@code project} folder beside the fixture, by their path in that folder. A fixture without the
   * folder has none.
   */
  static ProjectFiles manifests(final Path file) throws IOException {
    final Path own = file.resolveSibling("passing-project");
    final boolean passing =
        file.getFileName().toString().startsWith(PASSING) && Files.isDirectory(own);
    final Path project = passing ? own : file.resolveSibling("project");
    if (!Files.isDirectory(project)) {
      return ProjectFiles.empty();
    }
    final Map<String, String> texts = new TreeMap<>();
    try (Stream<Path> files = Files.walk(project)) {
      for (final Path manifest : files.filter(Files::isRegularFile).toList()) {
        final String name = manifest.getFileName().toString();
        if ("package.json".equals(name) || "pnpm-workspace.yaml".equals(name)) {
          texts.put(
              project.relativize(manifest).toString().replace('\\', '/'),
              Files.readString(manifest));
        }
      }
    }
    return new ProjectFiles(texts);
  }
}
