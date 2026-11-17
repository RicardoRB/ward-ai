package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds the rules of one preset that report one place twice. A rule A "covers" rule B when B
 * reports a place that A reports on A's own violating fixture; two rules are duplicates when each
 * covers the other, so a fixture that merely lacks a docstring does not make its rule a duplicate
 * of the docstring rule. A pair is named {@code <first id> + <second id>}, ids sorted.
 */
final class PresetDuplicates {
  private PresetDuplicates() {}

  /** A violating fixture file and the rule it belongs to. */
  record Fixture(String ruleId, Path file) {}

  /** Returns the violating fixtures of {@code rules}, under {@code rules/<language>/<rule>/}. */
  static List<Fixture> fixtures(final Path root, final List<RuleDefinition> rules) {
    final List<Fixture> files = new ArrayList<>();
    for (final RuleDefinition rule : rules) {
      final String id = rule.id();
      final AnalysisLanguage language = rule.languages().iterator().next();
      final Path directory =
          root.resolve(CatalogRow.key(language)).resolve(id.substring(id.indexOf('/') + 1));
      if (Files.isDirectory(directory)) {
        try (Stream<Path> listed = Files.list(directory)) {
          listed
              .filter(file -> file.getFileName().toString().startsWith("violating"))
              .filter(file -> file.getFileName().toString().endsWith("." + extension(language)))
              .sorted()
              .forEach(file -> files.add(new Fixture(id, file)));
        } catch (final IOException exception) {
          throw new UncheckedIOException(exception);
        }
      }
    }
    return files;
  }

  private static String extension(final AnalysisLanguage language) {
    return RuleFixtureSet.extension(language);
  }

  /** Runs all {@code rules} over each fixture and returns the pairs that cover each other. */
  static Set<String> pairs(
      final List<RuleDefinition> rules,
      final AnalysisLanguage language,
      final List<Fixture> fixtures
  ) throws IOException {
    final RuleEngine engine = new RuleEngine(BundledRuleMatchers.create());
    final RuleFixtureRunner runner = new RuleFixtureRunner();
    final Set<String> covers = new HashSet<>();
    for (final Fixture fixture : fixtures) {
      final SourceUnit unit = unit(fixture, language);
      final List<AnalysisFinding> found =
          engine.run(List.of(unit), runner.frontends(), rules).findings();
      final Set<String> own = ownPlaces(found, fixture.ruleId());
      found.stream()
          .filter(finding -> !finding.ruleId().equals(fixture.ruleId()))
          .filter(finding -> own.contains(place(finding)))
          .forEach(finding -> covers.add(fixture.ruleId() + " + " + finding.ruleId()));
    }
    final Set<String> pairs = new TreeSet<>();
    for (final String cover : covers) {
      final String[] ids = cover.split(" \\+ ");
      if (covers.contains(ids[1] + " + " + ids[0])) {
        pairs.add(name(ids[0], ids[1]));
      }
    }
    return pairs;
  }

  private static Set<String> ownPlaces(final List<AnalysisFinding> found, final String ruleId) {
    return found.stream()
        .filter(finding -> finding.ruleId().equals(ruleId))
        .map(PresetDuplicates::place)
        .collect(Collectors.toSet());
  }

  private static SourceUnit unit(final Fixture fixture, final AnalysisLanguage language)
      throws IOException {
    return new SourceUnit(
        fixture.file().getFileName().toString(),
        language,
        Files.readString(fixture.file())
    );
  }

  private static String name(final String first, final String second) {
    return first.compareTo(second) < 0 ? first + " + " + second : second + " + " + first;
  }

  private static String place(final AnalysisFinding finding) {
    return finding.line() + ":" + finding.column();
  }
}
