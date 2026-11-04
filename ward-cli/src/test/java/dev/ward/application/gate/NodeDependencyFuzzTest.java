package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.node.NodeProjectLayout.NodePackage;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.analysis.infrastructure.node.NodeDependencySupports;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Random edits to TypeScript and JavaScript files: after every edit, the graph of the incremental
 * run, which reads cached summaries, must equal the graph of a cold build.
 */
class NodeDependencyFuzzTest {
  private static final int SEQUENCES = 100;
  private static final int EDITS = 6;
  private static final int MAX_LINES = 4;
  private static final int LINE_KINDS = 6;
  private static final int NAME_VARIANTS = 3;
  private static final int EXPORT_STAR = 3;
  private static final int EXPORT_CONST = 4;
  private static final int TARGET_VARIANTS = 4;
  private static final String VERSION = "0.6.0";
  private static final List<String> PATHS =
      List.of("src/a.ts", "src/b.ts", "src/c.js", "src/d/index.ts", "src/e.mjs");
  private static final NodeProjectLayout LAYOUT =
      new NodeProjectLayout(
          Set.copyOf(PATHS),
          List.of(),
          List.of(new NodePackage("lib", "", Map.of(), null, null, null)));

  @Test
  void incrementalGraphsEqualColdBuildsAfterEveryEdit() {
    final int compared =
        IntStream.range(0, SEQUENCES).map(sequence -> checkSequence(new Random(sequence))).sum();

    assertThat(compared).isEqualTo(SEQUENCES * (EDITS + 1));
  }

  private static int checkSequence(final Random random) {
    final Map<String, String> entries = new HashMap<>();
    final AnalysisCache cache = AnalysisCacheFixtures.memory(entries);
    final Map<String, String> texts = new HashMap<>();
    PATHS.forEach(path -> texts.put(path, randomText(random)));
    for (int edit = 0; edit <= EDITS; edit++) {
      if (edit > 0) {
        texts.put(PATHS.get(random.nextInt(PATHS.size())), randomText(random));
      }
      final List<SourceUnit> units = units(texts);
      final DependencyGraph warm = graph(cache, units);
      final DependencyGraph cold = graph(AnalysisCacheFixtures.NO_CACHE, units);
      assertThat(warm).isEqualTo(cold);
    }
    return EDITS + 1;
  }

  private static DependencyGraph graph(final AnalysisCache cache, final List<SourceUnit> units) {
    final List<DependencySupport> supports = NodeDependencySupports.create(LAYOUT);
    final List<FrontendSupport> frontends =
        List.of(
            new FrontendSupport(
                AnalysisLanguage.TYPESCRIPT,
                new AntlrLanguageFrontend(),
                Set.of(RuleCapability.SYNTAX)),
            new FrontendSupport(
                AnalysisLanguage.JAVASCRIPT,
                new AntlrLanguageFrontend(),
                Set.of(RuleCapability.SYNTAX)));
    return new DependencyAnalysis(frontends, supports)
        .analyze(units, new AnalysisSummaryCache(cache, AnalysisCacheFixtures.codec(), VERSION))
        .graph();
  }

  private static List<SourceUnit> units(final Map<String, String> texts) {
    final List<SourceUnit> units = new ArrayList<>();
    for (final String path : PATHS) {
      final AnalysisLanguage language =
          path.endsWith(".js") || path.endsWith(".mjs")
              ? AnalysisLanguage.JAVASCRIPT
              : AnalysisLanguage.TYPESCRIPT;
      units.add(new SourceUnit(path, language, texts.get(path)));
    }
    return units;
  }

  /**
   * One to four lines of module references, exports, declarations and comments, in random order.
   */
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
      case 0 -> "import { x } from \"" + relativeTarget(random) + "\";";
      case 1 -> "import type { T } from \"" + relativeTarget(random) + "\";";
      case 2 -> "const r = require(\"" + relativeTarget(random) + "\");";
      case EXPORT_STAR -> "export * from \"" + relativeTarget(random) + "\";";
      case EXPORT_CONST -> "export const N" + random.nextInt(NAME_VARIANTS) + " = 1;";
      default -> "// import \"" + relativeTarget(random) + "\";";
    };
  }

  private static String relativeTarget(final Random random) {
    return switch (random.nextInt(TARGET_VARIANTS)) {
      case 0 -> "./a";
      case 1 -> "./b.js";
      case 2 -> "./d";
      default -> "lib";
    };
  }
}
