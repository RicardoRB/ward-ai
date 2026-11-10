package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.infrastructure.init.ConfigFiles;
import dev.ward.infrastructure.init.node.NodeConfigTemplates;
import dev.ward.infrastructure.init.node.NodeStrykerTemplate;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The devDependencies that Node init writes for each preset must not be reported as unused in a
 * project that init made. The inputs are the node-matrix fixtures; the devDependencies and config
 * files are the ones init writes for that preset, as its catalog and config templates name them.
 */
final class NodeInitDevDependenciesTest {

  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/node-matrix");
  private static final String TYPESCRIPT_RULE = "ward:typescript/knip-dev-dependencies";
  private static final String JAVASCRIPT_RULE = "ward:javascript/knip-dev-dependencies";
  private static final String TYPESCRIPT_FIXTURE = "typescript";
  private static final String VERSION = "^1.0.0";
  private static final String PROBE = "left-over";
  private static final String CASE = "case";
  private static final String CASE_TEXT = "export const value = 1;\n";
  private static final String TSCONFIG = "tsconfig.json";
  private static final String VITEST_MJS = "vitest.config.mjs";
  private static final String JEST_MJS = "jest.config.mjs";
  private static final String STRYKER_MJS = "stryker.config.mjs";
  private static final Map<String, RuleDefinition> RULES =
      CatalogRuleDefinitions.all().stream()
          .collect(Collectors.toMap(RuleDefinition::id, rule -> rule));

  /**
   * One init preset: the fixture it runs on, its devDependencies, and the config files it writes.
   */
  record InitCase(
      String fixture,
      AnalysisLanguage language,
      List<String> devDependencies,
      Map<String, String> configs) {}

  static Stream<InitCase> initCases() {
    return Stream.of(
        new InitCase(
            TYPESCRIPT_FIXTURE,
            AnalysisLanguage.TYPESCRIPT,
            List.of("typescript", "vitest", "@vitest/coverage-v8"),
            Map.of(VITEST_MJS, vitest(QualityPreset.STANDARD))),
        new InitCase(
            "javascript-esm",
            AnalysisLanguage.JAVASCRIPT,
            List.of("@vitest/coverage-v8"),
            Map.of(VITEST_MJS, vitest(QualityPreset.STANDARD))),
        new InitCase(
            TYPESCRIPT_FIXTURE,
            AnalysisLanguage.TYPESCRIPT,
            List.of(
                "typescript",
                "vitest",
                "@vitest/coverage-v8",
                "@stryker-mutator/core",
                "@stryker-mutator/vitest-runner"),
            Map.of(
                VITEST_MJS, vitest(QualityPreset.STRICT),
                STRYKER_MJS, stryker("vitest"))),
        new InitCase(
            "javascript-esm",
            AnalysisLanguage.JAVASCRIPT,
            List.of("jest", "@stryker-mutator/core", "@stryker-mutator/jest-runner"),
            Map.of(
                JEST_MJS,
                ConfigFiles.marker(ConfigFiles.JS_MARKER, QualityPreset.STRICT)
                    + NodeConfigTemplates.jest(QualityPreset.STRICT),
                STRYKER_MJS,
                stryker("jest"))));
  }

  @ParameterizedTest
  @MethodSource("initCases")
  void initDevDependenciesAreUsedByTheProjectInitMade(final InitCase test) throws IOException {
    assertThat(findings(test, test.devDependencies())).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("initCases")
  void aDevDependencyNothingUsesIsStillReported(final InitCase test) throws IOException {
    final List<String> withProbe = new ArrayList<>(test.devDependencies());
    withProbe.add(PROBE);

    assertThat(findings(test, withProbe)).hasSize(1);
  }

  private static List<AnalysisFinding> findings(
      final InitCase test, final List<String> devDependencies) throws IOException {
    final RuleDefinition rule =
        RULES.get(
            test.language() == AnalysisLanguage.TYPESCRIPT ? TYPESCRIPT_RULE : JAVASCRIPT_RULE);
    return new RuleFixtureRunner()
        .run(
            rule,
            CASE + extension(test.language()),
            CASE_TEXT,
            null,
            project(test, devDependencies));
  }

  /** The project init made: the fixture's sources, the preset's config files, and its manifest. */
  private static FixtureProject project(final InitCase test, final List<String> devDependencies)
      throws IOException {
    final Map<String, String> manifests = new HashMap<>();
    manifests.put("package.json", manifest(test.fixture(), devDependencies));
    if (TYPESCRIPT_FIXTURE.equals(test.fixture())) {
      manifests.put(TSCONFIG, "{}\n");
    }
    manifests.putAll(test.configs());
    return new FixtureProject(new ProjectFiles(manifests), sources(test));
  }

  private static String manifest(final String fixture, final List<String> devDependencies) {
    final String entries =
        devDependencies.stream()
            .map(name -> "\"" + name + "\": \"" + VERSION + "\"")
            .collect(Collectors.joining(", "));
    return "{\"name\": \""
        + fixture
        + "\", \"scripts\": {\"test\": \"vitest run\"}, \"devDependencies\": {"
        + entries
        + "}}";
  }

  /** The TypeScript or JavaScript files of the fixture, by path in the fixture. */
  private static List<SourceUnit> sources(final InitCase test) throws IOException {
    final Path root = FIXTURES.resolve(test.fixture());
    final List<SourceUnit> units = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final String name = file.getFileName().toString();
        if (FileSystemSourceUnits.language(name) == test.language()) {
          final String path = root.relativize(file).toString().replace('\\', '/');
          units.add(new SourceUnit(path, test.language(), Files.readString(file)));
        }
      }
    }
    return units;
  }

  private static String extension(final AnalysisLanguage language) {
    return language == AnalysisLanguage.TYPESCRIPT ? ".ts" : ".js";
  }

  /** The text init writes for a vitest config: its managed marker, then the preset's template. */
  private static String vitest(final QualityPreset preset) {
    return ConfigFiles.marker(ConfigFiles.JS_MARKER, preset) + NodeConfigTemplates.vitest(preset);
  }

  /**
   * The text init writes for a Stryker config: its managed marker, then the template for the
   * runner.
   */
  private static String stryker(final String runner) {
    return ConfigFiles.marker(ConfigFiles.JS_MARKER, QualityPreset.STRICT)
        + NodeStrykerTemplate.stryker(runner, QualityPreset.STRICT, Framework.NONE);
  }
}
