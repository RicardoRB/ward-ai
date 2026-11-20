package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import dev.ward.infrastructure.rule.catalog.CorpusLabels.Place;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Differential parity of the Rust corpus with the real Clippy (#698): every implemented Rust rule
 * with a Clippy counterpart runs over the files of {@link PinnedCorpus#rust()} next to {@code
 * clippy-driver}, which reads each file as a library crate of its own (the corpus is never built,
 * installed or run, so its dependencies do not resolve). A lint needs type information that an
 * unresolved file lacks, so only the files that Clippy compiles without an error are compared, and not those with an inner {@code #![cfg(..)]} attribute, which compile to nothing here. The
 * committed report {@code benchmark/rust-corpus-parity-report.txt} counts, per rule and lint, the
 * lines both report, those only Ward reports ({@code ward-extra}) and those only Clippy reports.
 * Every file with a {@code ward-extra} finding is labelled in {@code benchmark/rust-corpus-parity-labels.txt}; a
 * label {@code fp} fails the test unless {@code benchmark/rust-corpus-approximations.txt} lists it
 * with a reason and an open issue. Skipped unless {@code -Dward.parity.clippy} names {@code
 * clippy-driver} and {@code -Dward.corpus.rust} the extracted archives; {@code
 * -Dward.benchmark.write=true} rewrites the report and writes the unlabelled lines to {@code
 * target/rust-corpus-parity-extra.txt}.
 */
final class RustCorpusClippyParityTest {
  private static final Path RESOURCES = Path.of("src/test/resources/benchmark");
  private static final String REPORT = "rust-corpus-parity-report.txt";
  private static final String HEADER =
      "# Ward's Rust rules with a Clippy counterpart against the real Clippy over the corpus files"
          + " that clippy-driver compiles as a library crate without an error (see"
          + " RustCorpusClippyParityTest). Per rule and lint, counted per file: findings both report, ward-extra (only"
          + " Ward), clippy-only. Every ward-extra line is labelled in"
          + " rust-corpus-parity-labels.txt.\n"
          + "# Regenerate: ./mvnw -pl ward-cli -am -Dtest=RustCorpusClippyParityTest"
          + " -Dward.corpus.rust=<dir> -Dward.parity.clippy=<clippy-driver> -Djacoco.skip=true"
          + " -Dward.benchmark.write=true -Dsurefire.failIfNoSpecifiedTests=false test\n";

  /** Whether the run has a Clippy and the corpus. */
  static boolean configured() {
    return !System.getProperty("ward.parity.clippy", "").isBlank()
        && !System.getProperty("ward.corpus.rust", "").isBlank();
  }

  @Test
  @EnabledIf("configured")
  void everyLineOnlyWardReportsIsLabelledAndNoneIsFalse() throws IOException {
    final Path directory = Path.of(System.getProperty("ward.corpus.rust"));
    final List<SourceUnit> units = new ArrayList<>();
    for (final PinnedCorpus corpus : PinnedCorpus.rust()) {
      units.addAll(corpus.units(directory));
    }
    final Map<String, String> lints = lints();
    final String clippy = System.getProperty("ward.parity.clippy");
    final Set<String> clean = new TreeSet<>();
    final Map<String, Set<String>> clippyLines = new TreeMap<>();
    units.parallelStream().forEach(unit -> compare(clippy, directory, unit, lints, clean, clippyLines));
    final Map<String, Set<String>> wardLines = ward(units, lints.values(), clean);
    final Map<Place, String> labels =
        CorpusLabels.read(RESOURCES.resolve("rust-corpus-parity-labels.txt"));
    final List<Place> extra = new ArrayList<>();
    final String report = report(lints, clean, clippyLines, wardLines, extra);
    if (Boolean.getBoolean("ward.benchmark.write")) {
      Files.writeString(RESOURCES.resolve(REPORT), report);
      Files.writeString(
          Path.of("target", "rust-corpus-parity-extra.txt"),
          extra.stream()
              .map(place -> place.rule() + " | " + place.location() + "\n")
              .collect(Collectors.joining())
      );
    }
    final Map<Place, String> approved =
        CorpusLabels.read(RESOURCES.resolve("rust-corpus-approximations.txt"));
    assertThat(report).isEqualTo(Files.readString(RESOURCES.resolve(REPORT)));
    assertThat(extra.stream().filter(place -> !labels.containsKey(place)).toList())
        .as("ward-extra lines without a label")
        .isEmpty();
    assertThat(labels.keySet().stream().filter(place -> !extra.contains(place)).toList())
        .as("labels without a ward-extra line")
        .isEmpty();
    assertThat(
            labels.entrySet().stream()
                .filter(entry -> "fp".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(place -> !approved.containsKey(place))
                .toList()
        )
        .as("false positives without a reason and an open issue")
        .isEmpty();
  }

  /** The clippy lint (with {@code _}) to the Ward rule id, of the implemented rows. */
  private static Map<String, String> lints() {
    final Map<String, String> lints = new TreeMap<>();
    new RuleCatalogReader()
        .load().rows().stream()
        .filter(row -> row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.RUST))
        .forEach(
            row ->
                row.counterparts().getOrDefault("clippy", List.of())
                    .forEach(id -> lints.put(id.replace('-', '_'), row.idFor(AnalysisLanguage.RUST)))
        );
    return lints;
  }

  private static void compare(
      final String clippy,
      final Path directory,
      final SourceUnit unit,
      final Map<String, String> lints,
      final Set<String> clean,
      final Map<String, Set<String>> clippyLines
  ) {
    try {
      final String slash = unit.path().substring(unit.path().indexOf('/') + 1);
      final String folder = unit.path().substring(0, unit.path().indexOf('/'));
      final Path file =
          directory.resolve(PinnedCorpus.rust().stream()
              .filter(corpus -> corpus.name().equals(folder))
              .findFirst()
              .orElseThrow()
              .folder()).resolve(slash);
      final String output = ClippyRun.allLints(clippy, lints.keySet(), file);
      if (ClippyRun.compiled(output) && !unit.text().contains("#![cfg(")) {
        synchronized (clean) {
          clean.add(unit.path());
          ClippyRun.lines(output).forEach(
              (lint, found) ->
                  found.forEach(
                      line ->
                          clippyLines
                              .computeIfAbsent(lint, key -> new TreeSet<>())
                              .add(unit.path() + ":" + line)
                  )
          );
        }
      }
    } catch (final IOException e) {
      throw new java.io.UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, Set<String>> ward(
      final List<SourceUnit> units,
      final java.util.Collection<String> ids,
      final Set<String> clean
  ) {
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> ids.contains(rule.id()))
            .sorted(Comparator.comparing(RuleDefinition::id))
            .toList();
    final List<SourceUnit> compared = units.stream().filter(unit -> clean.contains(unit.path())).toList();
    final Map<String, Set<String>> lines = new TreeMap<>();
    new RuleEngine(BundledRuleMatchers.create())
        .run(compared, new RuleFixtureRunner().frontends(), rules).findings()
        .forEach(
            finding ->
                lines
                    .computeIfAbsent(finding.ruleId(), key -> new TreeSet<>())
                    .add(finding.file() + ":" + finding.line())
        );
    return lines;
  }

  /** The number of lines per file; Clippy and Ward anchor a multi-line finding on different lines. */
  private static Map<String, Integer> perFile(final Set<String> lines) {
    final Map<String, Integer> counts = new TreeMap<>();
    lines.forEach(line -> counts.merge(line.substring(0, line.lastIndexOf(':')), 1, Integer::sum));
    return counts;
  }

  private static Set<String> union(final Map<String, Integer> first, final Map<String, Integer> second) {
    final Set<String> files = new TreeSet<>(first.keySet());
    files.addAll(second.keySet());
    return files;
  }

  private static String report(
      final Map<String, String> lints,
      final Set<String> clean,
      final Map<String, Set<String>> clippyLines,
      final Map<String, Set<String>> wardLines,
      final List<Place> extra
  ) {
    final StringBuilder report = new StringBuilder(HEADER);
    report.append("files compared %d%n".formatted(clean.size()));
    final Map<String, String> byRule = new LinkedHashMap<>();
    lints.forEach((lint, rule) -> byRule.merge(rule, lint, (a, b) -> a + "," + b));
    byRule.forEach(
        (rule, lint) -> {
          final Map<String, Integer> ward = perFile(wardLines.getOrDefault(rule, Set.of()));
          final Set<String> toolLines = new TreeSet<>();
          for (final String name : lint.split(",")) {
            toolLines.addAll(clippyLines.getOrDefault(name, Set.of()));
          }
          final Map<String, Integer> tool = perFile(toolLines);
          int both = 0;
          int only = 0;
          int clippyOnly = 0;
          for (final String file : new TreeSet<>(union(ward, tool))) {
            final int w = ward.getOrDefault(file, 0);
            final int t = tool.getOrDefault(file, 0);
            both += Math.min(w, t);
            only += Math.max(0, w - t);
            clippyOnly += Math.max(0, t - w);
            if (w > t) {
              extra.add(new Place(rule, file));
            }
          }
          report.append(
              "%s | clippy %s | both %d | ward-extra %d | clippy-only %d%n"
                  .formatted(rule, lint.replace('_', '-'), both, only, clippyOnly)
          );
        }
    );
    return report.toString();
  }
}
