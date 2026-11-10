package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.FileAnalysisCache;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A cached run must report what a cold run reports, for any edits and any damaged entries. */
class AnalysisCacheEquivalenceTest {
  private static final int SEQUENCES = 200;
  private static final int EDITS = 6;
  private static final int LINE_COUNT = 5;
  private static final List<String> TEXTS =
      List.of("class A {}", "long body", "short", "BROKEN", "a much longer text", "x");
  private static final UnitResultCache RESULTS =
      new UnitResultCache(
          new RuleEngine(matcher()), AnalysisCacheFixtures.codec(), "0.6.0", List.of());
  private static final List<FrontendSupport> FRONTENDS =
      List.of(
          new FrontendSupport(AnalysisLanguage.JAVA, frontend(), Set.of(RuleCapability.SYNTAX)));
  private static final RuleDefinition RULE =
      new RuleDefinition(
          "ward:java/long-body",
          AnalysisLanguage.JAVA,
          RuleCapability.SYNTAX,
          AnalysisSeverity.WARNING,
          "flag");

  @TempDir Path directory;

  @Test
  void cachedRunsMatchColdRunsAcrossFuzzedEdits() {
    for (int sequence = 0; sequence < SEQUENCES; sequence++) {
      final Random random = seeded(sequence);
      final AnalysisCache cache = cacheFor(sequence);
      final String[] texts = startingTexts();
      for (int edit = 0; edit < EDITS; edit++) {
        texts[random.nextInt(texts.length)] = TEXTS.get(random.nextInt(TEXTS.size()));
        final List<SourceUnit> units = units(texts);

        final RuleRunResult warm = RESULTS.run(cache, units, FRONTENDS, List.of(RULE));
        final RuleRunResult cold =
            RESULTS.run(AnalysisCacheFixtures.NO_CACHE, units, FRONTENDS, List.of(RULE));

        assertThat(warm).isEqualTo(cold);
      }
    }
  }

  @Test
  void damagedEntryIsAnalyzedAgainWhileTheOthersStayReused() throws IOException {
    final Path cacheDirectory = directory.resolve("damaged");
    final AnalysisCache cache = new FileAnalysisCache(cacheDirectory);
    final List<SourceUnit> units = units("long body", "class B {}", "short");
    final RuleRunResult cold =
        RESULTS.run(AnalysisCacheFixtures.NO_CACHE, units, FRONTENDS, List.of(RULE));
    RESULTS.run(cache, units, FRONTENDS, List.of(RULE));

    for (final Path entry : entries(cacheDirectory)) {
      final String original = Files.readString(entry);
      Files.writeString(entry, original.substring(0, original.length() / 2));

      assertThat(RESULTS.run(cache, units, FRONTENDS, List.of(RULE))).isEqualTo(cold);
      assertThat(Files.readString(entry)).isEqualTo(original);
    }
  }

  private AnalysisCache cacheFor(final int sequence) {
    return new FileAnalysisCache(directory.resolve("seq-" + sequence));
  }

  private static Random seeded(final int sequence) {
    return new Random(sequence);
  }

  private static String[] startingTexts() {
    return new String[] {"class A {}", "class B {}", "class C {}"};
  }

  private static List<Path> entries(final Path cacheDirectory) throws IOException {
    try (Stream<Path> files = Files.list(cacheDirectory)) {
      return files.sorted().toList();
    }
  }

  private static List<SourceUnit> units(final String... texts) {
    return List.of(
        new SourceUnit("src/A.java", AnalysisLanguage.JAVA, texts[0]),
        new SourceUnit("src/B.java", AnalysisLanguage.JAVA, texts[1]),
        new SourceUnit("src/C.java", AnalysisLanguage.JAVA, texts[2]));
  }

  private static LanguageFrontend frontend() {
    return unit -> {
      if (unit.text().contains("BROKEN")) {
        throw new AnalysisFailedException("syntax error");
      }
      final String flag = unit.text().contains("long") ? "1" : "0";
      final int line = 1 + unit.text().length() % LINE_COUNT;
      return new ParseResult(
          new SyntaxNode("unit", new Span(line, 1, line, 2, 0, 1), Map.of("flag", flag), List.of()),
          List.of());
    };
  }

  private static RuleMatcher matcher() {
    return definition ->
        new CompiledRule() {
          @Override
          public RuleDefinition definition() {
            return definition;
          }

          @Override
          public List<RuleMatch> matches(final SyntaxNode root) {
            return "1".equals(root.fields().get("flag"))
                ? List.of(new RuleMatch(root.span(), "long body"))
                : List.of();
          }
        };
  }
}
