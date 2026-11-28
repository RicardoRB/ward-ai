package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.WorkLimit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The keys and results of a run do not depend on how many threads compute the keys of the units. */
class ParallelUnitKeysTest {
  private static final String RULE = "ward:java/path-injection";
  private static final int SERVICES = 12;
  private static final int FOUR = 4;
  private static final List<Integer> OTHER_THREADS = List.of(2, FOUR, 8);
  private static final String ENTITY =
      "package gen;\npublic class Table {\n  public String getGenPath() { return null; }\n}\n";
  private static final String CONTROLLER =
      "package gen;\nimport org.springframework.web.bind.annotation.RequestBody;\n"
          + "public class TableController {\n  void save(@RequestBody Table table) {}\n}\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final List<RuleDefinition> rules =
      new ClasspathRuleSource().load().rules().stream()
          .filter(rule -> RULE.equals(rule.id()))
          .map(rule -> RuleOptions.apply(rule, Map.of("min-confidence", "possible")))
          .toList();

  private List<SourceUnit> units() {
    final List<SourceUnit> units = new ArrayList<>();
    units.add(new SourceUnit("gen/Table.java", AnalysisLanguage.JAVA, ENTITY));
    units.add(new SourceUnit("gen/TableController.java", AnalysisLanguage.JAVA, CONTROLLER));
    IntStream.range(0, SERVICES).forEach(
        index -> units.add(
            new SourceUnit(
                "gen/Service" + index + ".java",
                AnalysisLanguage.JAVA,
                "package gen;\nimport java.io.File;\npublic class Service" + index + " {\n"
                    + "  File target(Table table) {\n    return new File(table.getGenPath());\n"
                    + "  }\n}\n"
            )
        )
    );
    return units;
  }

  private List<AnalysisFinding> run(final int threads, final Map<String, String> store) {
    final List<SourceUnit> units = units();
    return new UnitResultCache(
        new RuleEngine(new DeclarativeRuleMatcher(), new WorkLimit(threads)),
        AnalysisCacheFixtures.codec(),
        "test",
        new JvmDependencyAdapters().forProject(root, units)
    )
        .run(AnalysisCacheFixtures.memory(store), units, frontends, rules)
        .findings();
  }

  /** The keys a run on {@code threads} stores, after checking that it finds what {@code serial}. */
  private Set<String> storedKeys(final int threads, final List<AnalysisFinding> serial) {
    final Map<String, String> store = new ConcurrentHashMap<>();
    assertThat(run(threads, store)).isEqualTo(serial);
    return store.keySet();
  }

  @Test
  void theFindingsAndTheStoredKeysAreTheSameForEveryNumberOfThreads() {
    final Map<String, String> serialStore = new ConcurrentHashMap<>();
    final List<AnalysisFinding> serial = run(1, serialStore);

    assertThat(serial).hasSize(SERVICES);
    for (final int threads : OTHER_THREADS) {
      assertThat(storedKeys(threads, serial)).isEqualTo(serialStore.keySet());
    }
  }

  @Test
  void aSecondRunOnManyThreadsReadsEveryResultTheFirstOneStored() {
    final Map<String, String> store = new ConcurrentHashMap<>();
    final List<AnalysisFinding> first = run(FOUR, store);
    final int stored = store.size();

    assertThat(run(FOUR, store)).isEqualTo(first);
    assertThat(store).hasSize(stored);
  }
}
