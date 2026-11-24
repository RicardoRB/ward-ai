package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Writes the findings of the {@code strict} preset and every security rule of one language over
 * its pinned projects as {@code rule | path:line | line of code}, for the differential report of
 * #760. It compares nothing: {@code corpus/diff-findings.sh} runs it at a base commit and at the
 * working tree and diffs the two files. It is self-contained on purpose, because the script
 * copies this one file into the base checkout. {@code -Dward.corpus.diff.language=<java|kotlin|
 * csharp|rust|python|node|swift>} selects the language, {@code -Dward.corpus.<language>=<dir>} names the extracted
 * archives and {@code -Dward.corpus.diff.out=<file>} the output.
 */
class CorpusDiffSmokeTest {
  @Test
  @EnabledIfSystemProperty(named = "ward.corpus.diff.out", matches = ".+")
  void writesTheFindingsOfTheLanguage() throws IOException {
    final String key = System.getProperty("ward.corpus.diff.language").toLowerCase(Locale.ROOT);
    final List<PinnedCorpus> pinned = PinnedCorpus.forLanguage(key);
    final Path directory = Path.of(System.getProperty("ward.corpus." + key));
    final Map<String, List<String>> lines = new HashMap<>();
    final List<SourceUnit> units = new ArrayList<>();
    for (final PinnedCorpus corpus : pinned) {
      for (final SourceUnit unit : corpus.units(directory)) {
        units.add(unit);
        lines.put(unit.path(), unit.text().lines().toList());
      }
    }
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> pinned.get(0).readBy(rule))
            .filter(
                rule ->
                    RulePreset.STRICT.selects(rule.presets()) || "security".equals(rule.category())
            )
            .sorted(Comparator.comparing(RuleDefinition::id))
            .toList();
    final List<AnalysisFinding> findings =
        new RuleEngine(BundledRuleMatchers.create())
            .run(units, new RuleFixtureRunner().frontends(), rules)
            .findings();
    final String text =
        findings.stream()
            .map(finding -> row(finding, lines))
            .distinct()
            .sorted()
            .collect(Collectors.joining("\n", "", "\n"));
    final Path out = Path.of(System.getProperty("ward.corpus.diff.out"));
    Files.createDirectories(out.toAbsolutePath().getParent());
    Files.writeString(out, text);
    assertThat(units).isNotEmpty();
  }

  private static String row(final AnalysisFinding finding, final Map<String, List<String>> lines) {
    final List<String> source = lines.getOrDefault(finding.file(), List.of());
    final int index = finding.line() - 1;
    final String code = index >= 0 && index < source.size() ? source.get(index).strip() : "";
    return "%s | %s:%d | %s".formatted(finding.ruleId(), finding.file(), finding.line(), code);
  }
}
