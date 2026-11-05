package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs Ward on each labeled corpus and scores the rules the corpus labels. Ward runs on shards of
 * each corpus, in work copies with a minimal {@code ward.toml} that selects only the ward gate and
 * those rules. The checkout of an external corpus is never changed.
 */
final class AccuracyRun {

  /** The profile of the minimal configuration, which runs only the ward gate. */
  static final String PROFILE = "accuracy";

  static final String LABELS_FILE = "labels.csv";
  static final String EXTERNAL_FILE = "external.csv";

  private static final Logger LOG = Logger.getLogger(AccuracyRun.class.getName());
  private static final String JAVA = "java";
  private static final String CHECK_NAME = "ward-accuracy";

  private final CommandRunner runner;
  private final CorpusFetcher fetcher;
  private final Path binary;
  private final Path cacheRoot;
  private final Path workRoot;

  AccuracyRun(
      final CommandRunner runner, final Path binary, final Path cacheRoot, final Path workRoot) {
    this.runner = runner;
    this.fetcher = new CorpusFetcher(runner);
    this.binary = binary;
    this.cacheRoot = cacheRoot;
    this.workRoot = workRoot;
  }

  /**
   * Scores every corpus under the directory: each subdirectory that holds a {@code labels.csv},
   * then each row of {@code external.csv}.
   *
   * @param accuracyDir the directory of the corpora
   * @return the scores of all corpora
   */
  AccuracyResult run(final Path accuracyDir) {
    final List<CorpusAccuracy> corpora = new ArrayList<>();
    for (final Path corpus : checkedInCorpora(accuracyDir)) {
      LOG.info(() -> "Scoring checked-in corpus " + corpus.getFileName());
      corpora.add(checkedIn(corpus));
    }
    for (final ExternalCorpus external : ExternalCorpus.read(accuracyDir.resolve(EXTERNAL_FILE))) {
      LOG.info(() -> "Scoring external corpus " + external.name());
      corpora.add(external(external));
    }
    return AccuracyResult.of(corpora);
  }

  private static List<Path> checkedInCorpora(final Path accuracyDir) {
    try (Stream<Path> children = Files.list(accuracyDir)) {
      return children
          .filter(child -> Files.isRegularFile(child.resolve(LABELS_FILE)))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not list the corpora in " + accuracyDir, e);
    }
  }

  private CorpusAccuracy checkedIn(final Path corpus) {
    final String name = Objects.requireNonNull(corpus.getFileName()).toString();
    final List<AccuracyLabel> labels = LabelCsv.read(corpus.resolve(LABELS_FILE));
    return score(name, CorpusAccuracy.CHECKED_IN, corpus, labels);
  }

  private CorpusAccuracy external(final ExternalCorpus external) {
    final CorpusEntry entry =
        new CorpusEntry(JAVA, external.name(), external.url(), external.commit());
    final Path checkout = fetcher.fetch(entry, cacheRoot);
    final List<AccuracyLabel> labels =
        OwaspLabels.read(checkout.resolve(external.labels()), external.root());
    return score(external.name(), external.commit(), checkout, labels);
  }

  private CorpusAccuracy score(
      final String name, final String commit, final Path source, final List<AccuracyLabel> labels) {
    if (labels.isEmpty()) {
      throw new IllegalArgumentException("Corpus " + name + " has no labels");
    }
    final List<String> rules =
        labels.stream().map(AccuracyLabel::rule).distinct().sorted().toList();
    requireLabeledFiles(name, source, labels);
    final Set<String> extensions =
        labels.stream()
            .map(label -> CorpusShard.extension(label.file()))
            .collect(Collectors.toSet());
    final List<String> files = CorpusShard.files(source);
    final List<String> sources = CorpusShard.sourceFiles(files, extensions);
    final Layout layout = new Layout(name, source, files, sources);
    final Set<WardFinding> findings = new TreeSet<>(WardFinding.ORDER);
    for (final List<String> shard : CorpusShard.of(sources)) {
      findings.addAll(findings(layout, shard, rules));
    }
    final List<RuleScore> scores =
        rules.stream().map(rule -> AccuracyScore.score(rule, labels, findings)).toList();
    return new CorpusAccuracy(name, commit, scores);
  }

  /** A label that names a missing file is an error, so a typo never reads as a miss. */
  private static void requireLabeledFiles(
      final String name, final Path source, final List<AccuracyLabel> labels) {
    for (final AccuracyLabel label : labels) {
      if (!Files.isRegularFile(source.resolve(label.file()))) {
        throw new IllegalArgumentException(
            "Corpus " + name + " labels a missing file: " + label.file());
      }
    }
  }

  /** Runs Ward on one shard and reads its findings. */
  private Set<WardFinding> findings(
      final Layout layout, final List<String> shard, final List<String> rules) {
    final Path work =
        workRoot.resolve(layout.name()).resolve("shard-" + layout.sources().indexOf(shard.get(0)));
    FileTrees.delete(work);
    CorpusShard.populate(
        layout.root(), work, layout.files(), Set.copyOf(shard), Set.copyOf(layout.sources()));
    writeConfig(work, rules);
    final CommandOutput output =
        runner.output(
            List.of(binary.toString(), "check", "--format", "json", "--profile", PROFILE), work);
    if (output.stdout().isBlank()) {
      throw new IllegalStateException("Ward printed no JSON for corpus " + layout.name());
    }
    return WardFindings.read(output.stdout());
  }

  /** The files of one corpus: its root, every file, and its source files. */
  private record Layout(String name, Path root, List<String> files, List<String> sources) {}

  /** Writes the minimal configuration: one ward check that runs the given rules only. */
  static void writeConfig(final Path work, final List<String> rules) {
    final String quoted =
        rules.stream().map(rule -> "\"" + rule + "\"").collect(Collectors.joining(", "));
    final String toml =
        "version = 2\n\n[checks."
            + CHECK_NAME
            + "]\nuses = \"ward\"\nprofiles = [\""
            + PROFILE
            + "\"]\nrules = ["
            + quoted
            + "]\n";
    final Path file = work.resolve("ward.toml");
    try {
      // A work copy may hard-link the corpus file, so the link is replaced rather than written.
      Files.deleteIfExists(file);
      Files.writeString(
          file,
          toml,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write the accuracy configuration", e);
    }
  }
}
