package dev.ward.bench;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Entry point: measures the gate baseline of the native Ward executable on the pinned corpus and
 * writes a versioned JSON result plus a short summary on stdout. Exit code is 0 only when every
 * entry and job completed.
 */
public final class AnalysisBenchmark {

  private static final Logger LOG = Logger.getLogger(AnalysisBenchmark.class.getName());
  private static final String USAGE =
      "Usage: --binary <ward native executable> --manifest <corpus.csv>"
          + " --out <result.json> [--repeats <n>] [--cache <dir>] [--work <dir>]";
  private static final int DEFAULT_REPEATS = 5;
  private static final int MIN_REPEATS = 1;
  private static final int OPTION_PAIR = 2;
  private static final Path DEFAULT_CACHE = Path.of("target", "bench-corpus");
  private static final Path DEFAULT_WORK = Path.of("target", "bench-work");
  private static final int EXIT_COMPLETED = 0;
  private static final int EXIT_INCOMPLETE = 1;
  private static final int EXIT_USAGE = 2;

  private AnalysisBenchmark() {}

  /** Runs the benchmark with the command-line arguments and exits with its status. */
  public static void main(final String[] args) {
    System.exit(run(args, System.out));
  }

  static int run(final String[] args, final PrintStream out) {
    final Map<String, String> options = parse(args);
    if (options.isEmpty()) {
      LOG.severe(USAGE);
      return EXIT_USAGE;
    }
    try {
      return benchmark(options, out);
    } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
      LOG.severe(() -> "Benchmark stopped: " + e.getMessage());
      return EXIT_INCOMPLETE;
    }
  }

  private static int benchmark(final Map<String, String> options, final PrintStream out) {
    final Path binary = Path.of(options.get("binary")).toAbsolutePath();
    final NativeExecutable executable = NativeExecutable.verify(binary);
    final List<CorpusEntry> entries = CorpusManifest.read(Path.of(options.get("manifest")));
    final int repeats = repeats(options);
    LOG.info(() -> "Measuring " + entries.size() + " corpus entries");
    final BenchmarkRunner benchmark =
        new BenchmarkRunner(
            new ProcessCommandRunner(),
            binary,
            Path.of(options.getOrDefault("cache", DEFAULT_CACHE.toString())).toAbsolutePath(),
            Path.of(options.getOrDefault("work", DEFAULT_WORK.toString())).toAbsolutePath(),
            repeats);
    final BenchmarkResult result = benchmark.run(entries, executable);
    write(Path.of(options.get("out")), ResultJson.render(result, Instant.now()));
    out.print(Summary.render(result));
    return result.completed() ? EXIT_COMPLETED : EXIT_INCOMPLETE;
  }

  private static int repeats(final Map<String, String> options) {
    final int repeats =
        Integer.parseInt(options.getOrDefault("repeats", String.valueOf(DEFAULT_REPEATS)));
    if (repeats < MIN_REPEATS) {
      throw new IllegalArgumentException("--repeats must be at least " + MIN_REPEATS);
    }
    return repeats;
  }

  private static void write(final Path out, final String json) {
    try {
      final Path parent = out.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(out, json, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write the result file", e);
    }
  }

  /** Parses {@code --name value} pairs; returns an empty map when a required option is missing. */
  private static Map<String, String> parse(final String... args) {
    final Map<String, String> options = new HashMap<>();
    for (int i = 0; i + 1 < args.length; i += OPTION_PAIR) {
      if (!args[i].startsWith("--")) {
        return Map.of();
      }
      options.put(args[i].substring(2), args[i + 1]);
    }
    if (!options.containsKey("binary")
        || !options.containsKey("manifest")
        || !options.containsKey("out")) {
      return Map.of();
    }
    return options;
  }
}
