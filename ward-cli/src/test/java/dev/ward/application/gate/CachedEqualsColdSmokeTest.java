package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Compares a cold run with cached runs over the sources below a directory, as story #604 requires
 * of the Java and Kotlin corpora: a first run with an empty cache, a run that finds every result
 * stored, and runs after edits of some files all print what a cold run of the same files prints.
 * It is skipped unless {@code -Dward.cache.corpus=<dir>} names the directory of the extracted
 * archives and {@code -Dward.cache.corpus.language=java|kotlin} the language; the times of each
 * run go to the standard error.
 */
class CachedEqualsColdSmokeTest {
  private static final Logger LOG = Logger.getLogger(CachedEqualsColdSmokeTest.class.getName());
  private static final long NANOS_PER_MILLI = 1_000_000L;
  private static final int EDITED = 25;

  private final Map<String, String> entries = new HashMap<>();
  private final List<String> resultWrites = new ArrayList<>();
  private final AnalysisCache warm = recording(AnalysisCacheFixtures.memory(entries));
  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private AnalysisCache recording(final AnalysisCache inner) {
    return new AnalysisCache() {
      @Override
      public Optional<String> read(final String key) {
        return inner.read(key);
      }

      @Override
      public void write(final String key, final String value) {
        inner.write(key, value);
        if (AnalysisCacheFixtures.codec().decode(key, value).isPresent()) {
          resultWrites.add(key);
        }
      }
    };
  }

  @Test
  @DisplayName("cached equals cold")
  @EnabledIfSystemProperty(named = "ward.cache.corpus", matches = ".+")
  void cachedEqualsCold() throws IOException {
    // Given a corpus of sources and the rules of its language
    final Path directory = Path.of(System.getProperty("ward.cache.corpus"));
    final AnalysisLanguage language =
        "kotlin".equals(System.getProperty("ward.cache.corpus.language"))
            ? AnalysisLanguage.KOTLIN
            : AnalysisLanguage.JAVA;
    final List<SourceUnit> units = CorpusSources.units(directory, language);
    final List<RuleDefinition> rules = rules(language);

    // When a cold run, a first cached run, a fully cached run and runs after edits finish
    final List<AnalysisFinding> cold = timed(
        "cold",
        directory,
        AnalysisCacheFixtures.NO_CACHE,
        units,
        rules
    );
    final List<AnalysisFinding> first = timed("cached, first", directory, warm, units, rules);
    resultWrites.clear();
    final List<AnalysisFinding> second = timed("cached, all stored", directory, warm, units, rules);
    final int writesWhenStored = resultWrites.size();
    resultWrites.clear();
    final List<SourceUnit> commented = edited(units, "\n// a note that changes no summary\n");
    final List<AnalysisFinding> afterComments = timed(
        "cached, comments",
        directory,
        warm,
        commented,
        rules
    );
    final int writesAfterComments = resultWrites.size();
    final List<AnalysisFinding> coldAfterComments =
        timed("cold, comments", directory, AnalysisCacheFixtures.NO_CACHE, commented, rules);

    // Then their findings are identical
    LOG.log(
        Level.INFO,
        "units={0} findings={1} writes: stored={2} after comment edits={3}",
        new Object[] {units.size(), cold.size(), writesWhenStored, writesAfterComments}
    );
    assertThat(first).isEqualTo(cold);
    assertThat(second).isEqualTo(cold);
    assertThat(writesWhenStored).isZero();
    assertThat(afterComments).isEqualTo(coldAfterComments);
  }

  private List<AnalysisFinding> timed(
      final String label,
      final Path directory,
      final AnalysisCache cache,
      final List<SourceUnit> units,
      final List<RuleDefinition> rules
  ) {
    final long start = System.nanoTime();
    final RuleRunResult result =
        new UnitResultCache(
            new RuleEngine(BundledRuleMatchers.create()),
            AnalysisCacheFixtures.codec(),
            "test",
            new JvmDependencyAdapters().forProject(directory, units)
        )
            .run(cache, units, frontends, rules);
    LOG.log(
        Level.INFO,
        "{0}: {1} ms",
        new Object[] {label, (System.nanoTime() - start) / NANOS_PER_MILLI}
    );
    assertThat(result.executionErrors()).isEmpty();
    assertThat(result.notRun()).isEmpty();
    return result.findings();
  }

  /** Appends the text to every {@value #EDITED}th-spaced file, enough to touch some callees. */
  private static List<SourceUnit> edited(final List<SourceUnit> units, final String suffix) {
    final int step = Math.max(1, units.size() / EDITED);
    final List<SourceUnit> result = new ArrayList<>();
    for (int index = 0; index < units.size(); index++) {
      final SourceUnit unit = units.get(index);
      result.add(
          index % step == 0
              ? new SourceUnit(unit.path(), unit.language(), unit.text() + suffix)
              : unit
      );
    }
    return result;
  }

  private static List<RuleDefinition> rules(final AnalysisLanguage language) {
    return new ClasspathRuleSource().load().rules().stream()
        .filter(rule -> rule.covers(language))
        .filter(rule -> rule.capability() != RuleCapability.TYPES)
        .filter(
            rule ->
                RulePreset.STANDARD.selects(rule.presets()) || "security".equals(rule.category())
        )
        .toList();
  }
}
