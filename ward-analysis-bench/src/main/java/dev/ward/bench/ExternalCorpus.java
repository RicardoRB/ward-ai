package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One external labeled corpus, a row of {@code accuracy/external.csv}: a repository pinned by full
 * commit SHA, the file of its labels and the format of that file.
 *
 * @param name the corpus name, a plain directory name
 * @param url the repository to fetch
 * @param commit the full commit SHA to fetch and check out
 * @param labels the labels file, relative to the checkout root
 * @param format the label format; only {@code owasp-benchmark} is read
 * @param root the test source directory, relative to the checkout root
 */
record ExternalCorpus(
    String name, String url, String commit, String labels, String format, String root) {

  /** The format of OWASP Benchmark's expected results. */
  static final String OWASP = "owasp-benchmark";

  private static final String HEADER = "name,url,commit,labels,format,root";
  private static final int COLUMNS = 6;
  private static final int NAME = 0;
  private static final int URL = 1;
  private static final int COMMIT = 2;
  private static final int LABELS = 3;
  private static final int FORMAT = 4;
  private static final int ROOT = 5;

  /**
   * Reads the external corpora.
   *
   * @param csv the {@code external.csv} file
   * @return the corpora, in file order; an absent file gives none
   */
  static List<ExternalCorpus> read(final Path csv) {
    if (!Files.exists(csv)) {
      return List.of();
    }
    try {
      return parse(Files.readAllLines(csv, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + csv, e);
    }
  }

  static List<ExternalCorpus> parse(final List<String> lines) {
    final List<String> rows =
        lines.stream().map(String::strip).filter(line -> !line.isEmpty()).toList();
    if (rows.isEmpty() || !HEADER.equals(rows.get(0))) {
      throw new IllegalArgumentException("External corpora must start with the header " + HEADER);
    }
    final List<ExternalCorpus> corpora = new ArrayList<>();
    for (final String row : rows.subList(1, rows.size())) {
      corpora.add(corpus(row));
    }
    return List.copyOf(corpora);
  }

  private static ExternalCorpus corpus(final String row) {
    final String[] columns = row.split(",", -1);
    if (columns.length != COLUMNS) {
      throw new IllegalArgumentException("External corpus row must have six columns: " + row);
    }
    final String format = columns[FORMAT];
    if (!OWASP.equals(format)) {
      throw new IllegalArgumentException(
          "Unknown label format for " + columns[NAME] + ": " + format);
    }
    return new ExternalCorpus(
        columns[NAME], columns[URL], columns[COMMIT], columns[LABELS], format, columns[ROOT]);
  }
}
