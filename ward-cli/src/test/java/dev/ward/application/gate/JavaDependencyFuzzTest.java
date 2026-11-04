package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Edits the Java files of the java-resolution fixture at random and checks that the incremental
 * dependency graph, which reuses stored summaries, equals a cold build after every edit.
 */
class JavaDependencyFuzzTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/java-resolution");
  private static final int SEQUENCES = 25;
  private static final int EDITS = 5;
  private static final String VERSION = "0.6.0";
  private static final List<String> SNIPPETS =
      List.of(
          "import shop.domain.Price;",
          "import shop.domain.*;",
          "import shop.domain.Discount;",
          "import static shop.domain.Prices.ZERO;",
          "import shop.legacy.Discount;",
          "private Price price;",
          "Cart cart;",
          "shop.domain.Discount discount;",
          "// Order.Line is only a comment",
          "");

  @Test
  void incrementalGraphEqualsTheColdBuildAfterEveryEdit() throws IOException {
    final Map<String, String> files = fixtureFiles();
    final int compared =
        IntStream.range(0, SEQUENCES)
            .map(sequence -> checkSequence(new Random(sequence), files))
            .sum();

    assertThat(compared).isEqualTo(SEQUENCES * (EDITS + 1));
  }

  /** Compares the incremental and cold graphs after the start and after each edit. */
  private static int checkSequence(final Random random, final Map<String, String> start) {
    final Map<String, String> texts = new HashMap<>(start);
    final AnalysisCache cache = AnalysisCacheFixtures.memory(new HashMap<>());
    final List<String> paths = List.copyOf(texts.keySet());
    for (int edit = 0; edit <= EDITS; edit++) {
      if (edit > 0) {
        final String path = paths.get(random.nextInt(paths.size()));
        texts.put(path, mutate(random, texts.get(path)));
      }
      final List<SourceUnit> units = units(texts);
      assertThat(graph(cache, units)).isEqualTo(coldGraph(units));
    }
    return EDITS + 1;
  }

  /** Inserts a snippet at a random line, or deletes one, so the edit may break the imports. */
  private static String mutate(final Random random, final String text) {
    final List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
    if (random.nextBoolean() && lines.size() > 1) {
      lines.remove(random.nextInt(lines.size()));
    } else {
      lines.add(random.nextInt(lines.size() + 1), SNIPPETS.get(random.nextInt(SNIPPETS.size())));
    }
    return String.join("\n", lines);
  }

  private static DependencyGraph graph(final AnalysisCache cache, final List<SourceUnit> units) {
    return analysis(units)
        .analyze(units, new AnalysisSummaryCache(cache, AnalysisCacheFixtures.codec(), VERSION))
        .graph();
  }

  private static DependencyGraph coldGraph(final List<SourceUnit> units) {
    return analysis(units)
        .analyze(
            units,
            new AnalysisSummaryCache(
                AnalysisCacheFixtures.NO_CACHE, AnalysisCacheFixtures.codec(), VERSION))
        .graph();
  }

  /** The Java frontend and the Java adapters, which read the fixture's own source roots. */
  private static DependencyAnalysis analysis(final List<SourceUnit> units) {
    final FrontendSupport frontend =
        new FrontendSupport(
            AnalysisLanguage.JAVA, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    return new DependencyAnalysis(
        List.of(frontend),
        new ProjectDependencyAdapters()
            .forProject(FIXTURE.toAbsolutePath(), units, Optional.empty()));
  }

  private static List<SourceUnit> units(final Map<String, String> texts) {
    return texts.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> new SourceUnit(entry.getKey(), AnalysisLanguage.JAVA, entry.getValue()))
        .toList();
  }

  /** The Java sources of the fixture, keyed by project-relative path. */
  private static Map<String, String> fixtureFiles() throws IOException {
    final Map<String, String> files = new HashMap<>();
    try (Stream<Path> walk = Files.walk(FIXTURE)) {
      for (final Path file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
        files.put(FIXTURE.relativize(file).toString().replace('\\', '/'), Files.readString(file));
      }
    }
    return files;
  }
}
