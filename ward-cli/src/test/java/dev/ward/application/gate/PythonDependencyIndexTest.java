package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Runs the Python dependency adapters through the unit result cache on the uv workspace fixture,
 * and checks that the reverse-dependency index it stores holds the edge between the two members.
 */
final class PythonDependencyIndexTest {

  private static final Path WORKSPACE = Path.of("src/test/resources/fixtures/python-uv-workspace");
  private static final String IMPORTER = "packages/shop/src/shop/checkout.py";
  private static final String IMPORTED = "packages/billing/src/billing/invoice.py";

  @Test
  void reverseIndexStoresTheCrossMemberEdge() throws IOException {
    final Map<String, String> entries = new HashMap<>();
    final AnalysisCache cache =
        new AnalysisCache() {
          @Override
          public Optional<String> read(final String key) {
            return Optional.ofNullable(entries.get(key));
          }

          @Override
          public void write(final String key, final String value) {
            entries.put(key, value);
          }
        };
    final FrontendSupport python =
        new FrontendSupport(
            AnalysisLanguage.PYTHON, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));

    new UnitResultCache(
            new RuleEngine(new MetricMatcher()),
            AnalysisCacheFixtures.codec(),
            "test",
            new ProjectDependencyAdapters().forProject(WORKSPACE))
        .run(cache, pythonUnits(), List.of(python), List.of());

    assertThat(entries.values())
        .anyMatch(value -> value.contains(IMPORTER) && value.contains(IMPORTED));
  }

  private static List<SourceUnit> pythonUnits() throws IOException {
    try (Stream<Path> files = Files.walk(WORKSPACE)) {
      return files
          .filter(file -> file.toString().endsWith(".py"))
          .sorted()
          .map(
              file -> {
                final String path = WORKSPACE.relativize(file).toString().replace('\\', '/');
                return new SourceUnit(path, AnalysisLanguage.PYTHON, read(file));
              })
          .toList();
    }
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException exception) {
      throw new IllegalStateException("cannot read fixture " + file, exception);
    }
  }
}
