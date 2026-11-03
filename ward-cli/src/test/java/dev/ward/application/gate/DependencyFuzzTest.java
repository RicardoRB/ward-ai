package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DependencyFuzzTest {
  private static final int SEQUENCES = 200;
  private static final int EDITS = 6;
  private static final int MAX_LINES = 4;
  private static final int LINE_KINDS = 4;
  private static final int BODY_VARIANTS = 3;
  private static final List<String> PATHS = List.of("A.kt", "B.kt", "C.kt", "D.kt");

  @Test
  void incrementalRunsEqualColdBuildsAfterEveryEdit() {
    final int compared =
        IntStream.range(0, SEQUENCES).map(sequence -> checkSequence(new Random(sequence))).sum();

    assertThat(compared).isEqualTo(SEQUENCES * (EDITS + 1));
  }

  /** Checks the cold build against the incremental one after each state; returns the count. */
  private static int checkSequence(final Random random) {
    final Map<String, String> entries = new HashMap<>();
    final AnalysisCache cache = AnalysisCacheFixtures.memory(entries);
    final Map<String, String> texts = new HashMap<>();
    PATHS.forEach(path -> texts.put(path, randomText(random)));
    for (int edit = 0; edit <= EDITS; edit++) {
      if (edit > 0) {
        final String path = PATHS.get(random.nextInt(PATHS.size()));
        texts.put(path, randomText(random));
      }
      final List<SourceUnit> units = units(texts);
      assertSameAsColdBuild(cache, units);
    }
    return EDITS + 1;
  }

  private static void assertSameAsColdBuild(
      final AnalysisCache cache, final List<SourceUnit> units) {
    final DependencyFixtureLanguage incremental = new DependencyFixtureLanguage();
    final DependencyFixtureLanguage cold = new DependencyFixtureLanguage();
    final RuleRunResult warm = run(incremental, cache, units);
    final RuleRunResult fresh = run(cold, AnalysisCacheFixtures.NO_CACHE, units);

    assertThat(warm.findings()).isEqualTo(fresh.findings());
    assertThat(graph(incremental, cache, units))
        .isEqualTo(graph(cold, AnalysisCacheFixtures.NO_CACHE, units));
  }

  private static RuleRunResult run(
      final DependencyFixtureLanguage fixture,
      final AnalysisCache cache,
      final List<SourceUnit> units) {
    return new UnitResultCache(
            new RuleEngine(fixture.matcher()),
            AnalysisCacheFixtures.codec(),
            "0.6.0",
            fixture.dependencies())
        .run(cache, units, List.of(fixture.frontend()), List.of(DependencyFixtureLanguage.RULE));
  }

  private static DependencyGraph graph(
      final DependencyFixtureLanguage fixture,
      final AnalysisCache cache,
      final List<SourceUnit> units) {
    return new DependencyAnalysis(List.of(fixture.frontend()), fixture.dependencies())
        .analyze(units, new AnalysisSummaryCache(cache, AnalysisCacheFixtures.codec(), "0.6.0"))
        .graph();
  }

  private static List<SourceUnit> units(final Map<String, String> texts) {
    final List<SourceUnit> units = new ArrayList<>();
    PATHS.forEach(
        path ->
            units.add(new SourceUnit(path, DependencyFixtureLanguage.LANGUAGE, texts.get(path))));
    return units;
  }

  /** One to four lines of imports, exports, declarations and comments, in random order. */
  private static String randomText(final Random random) {
    final List<String> lines = new ArrayList<>();
    final int count = 1 + random.nextInt(MAX_LINES);
    for (int line = 0; line < count; line++) {
      lines.add(randomLine(random));
    }
    return String.join("\n", lines);
  }

  private static String randomLine(final Random random) {
    return switch (random.nextInt(LINE_KINDS)) {
      case 0 -> "import " + PATHS.get(random.nextInt(PATHS.size())).replace(".kt", "");
      case 1 -> "export " + (random.nextBoolean() ? "N" : "M");
      case 2 -> "declare Q";
      default -> "// body " + random.nextInt(BODY_VARIANTS);
    };
  }
}
