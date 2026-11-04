package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Edits random Python modules and checks, after every edit, that the incremental dependency graph
 * equals a cold build. It uses the real Python adapters and the ANTLR frontend, so the summaries
 * are cached per file and the edges are resolved again on each run.
 */
final class PythonDependencyFuzzTest {

  private static final int SEQUENCES = 100;
  private static final int EDITS = 5;
  private static final int MAX_LINES = 4;
  private static final String VERSION = "0.6.0";
  private static final List<String> PATHS =
      List.of("shop/a.py", "shop/b.py", "shop/pkg/__init__.py", "shop/pkg/c.py");
  private static final List<String> STATEMENTS =
      List.of(
          "import shop.a",
          "import shop.pkg.c as c",
          "from shop import b",
          "from . import a",
          "from .. import b",
          "from shop.pkg import *",
          "from .c import value",
          "def f(): ...",
          "value = 1",
          "__all__ = ['value', 'f']",
          "# note");
  private static final FrontendSupport PYTHON =
      new FrontendSupport(
          AnalysisLanguage.PYTHON, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));

  @Test
  void incrementalGraphsEqualColdBuildsAfterEveryEdit() {
    final int compared =
        IntStream.range(0, SEQUENCES).map(sequence -> checkSequence(new Random(sequence))).sum();

    assertThat(compared).isEqualTo(SEQUENCES * (EDITS + 1));
  }

  /** Checks the cold graph against the incremental one after each state; returns the count. */
  private static int checkSequence(final Random random) {
    final Map<String, String> entries = new HashMap<>();
    final AnalysisCache cache = AnalysisCacheFixtures.memory(entries);
    final Map<String, String> texts = new HashMap<>();
    PATHS.forEach(path -> texts.put(path, randomText(random)));
    for (int edit = 0; edit <= EDITS; edit++) {
      if (edit > 0) {
        texts.put(PATHS.get(random.nextInt(PATHS.size())), randomText(random));
      }
      assertSameAsColdBuild(cache, units(texts));
    }
    return EDITS + 1;
  }

  private static void assertSameAsColdBuild(
      final AnalysisCache cache, final List<SourceUnit> units) {
    final DependencyGraph warm = graph(cache, units);
    final DependencyGraph cold = graph(AnalysisCacheFixtures.NO_CACHE, units);
    assertThat(warm).isEqualTo(cold);
    runIndex(cache, units);
  }

  /** The graph of one run, with the summaries read from and written to {@code cache}. */
  private static DependencyGraph graph(final AnalysisCache cache, final List<SourceUnit> units) {
    return new DependencyAnalysis(
            List.of(PYTHON), new PythonDependencyAdapters().forProject(root(), units))
        .analyze(units, new AnalysisSummaryCache(cache, AnalysisCacheFixtures.codec(), VERSION))
        .graph();
  }

  /** Runs the unit cache too, so the stored reverse index is rewritten after each edit. */
  private static void runIndex(final AnalysisCache cache, final List<SourceUnit> units) {
    new UnitResultCache(
            new RuleEngine(new MetricMatcher()),
            AnalysisCacheFixtures.codec(),
            VERSION,
            new PythonDependencyAdapters().forProject(root(), units))
        .run(cache, units, List.of(PYTHON), List.of());
  }

  /** The project root: it has no pyproject.toml, so the layout is the flat project root. */
  private static Path root() {
    return Path.of("src/test/resources/fixtures/python-resolution-fuzz-none");
  }

  private static List<SourceUnit> units(final Map<String, String> texts) {
    final List<SourceUnit> units = new ArrayList<>();
    PATHS.forEach(
        path -> units.add(new SourceUnit(path, AnalysisLanguage.PYTHON, texts.get(path))));
    return units;
  }

  /** One to four module-level statements, in random order. */
  private static String randomText(final Random random) {
    final List<String> lines = new ArrayList<>();
    final int count = 1 + random.nextInt(MAX_LINES);
    for (int line = 0; line < count; line++) {
      lines.add(STATEMENTS.get(random.nextInt(STATEMENTS.size())));
    }
    return String.join("\n", lines) + "\n";
  }
}
