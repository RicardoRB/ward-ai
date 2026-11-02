package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads {@code corpus.csv}: a header line followed by {@code language,name,url,commit} rows. */
final class CorpusManifest {

  private static final String HEADER = "language,name,url,commit";
  private static final int COLUMNS = 4;
  private static final int FIRST = 0;
  private static final int NAME = 1;
  private static final int URL = 2;
  private static final int COMMIT = 3;

  private CorpusManifest() {}

  static List<CorpusEntry> read(final Path manifest) {
    try {
      return parse(Files.readAllLines(manifest, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the corpus manifest", e);
    }
  }

  static List<CorpusEntry> parse(final List<String> lines) {
    final List<String> rows =
        lines.stream().map(String::strip).filter(line -> !line.isEmpty()).toList();
    if (rows.isEmpty() || !HEADER.equals(rows.get(0))) {
      throw new IllegalArgumentException("Corpus manifest must start with the header " + HEADER);
    }
    final List<CorpusEntry> entries = new ArrayList<>();
    for (final String row : rows.subList(1, rows.size())) {
      final String[] columns = row.split(",", -1);
      if (columns.length != COLUMNS) {
        throw new IllegalArgumentException("Corpus row must have four columns: " + columns[0]);
      }
      entries.add(new CorpusEntry(columns[FIRST], columns[NAME], columns[URL], columns[COMMIT]));
    }
    return List.copyOf(entries);
  }
}
