package dev.ward.bench;

import java.util.regex.Pattern;

/** One pinned corpus repository, read from a row of {@code corpus.csv}. */
record CorpusEntry(String language, String name, String url, String commit) {

  private static final Pattern FULL_SHA = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
  private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

  CorpusEntry {
    require(language, "language");
    require(url, "url");
    if (!SAFE_NAME.matcher(name).matches() || name.startsWith(".")) {
      throw new IllegalArgumentException("Corpus name must be a plain directory name: " + name);
    }
    if (!FULL_SHA.matcher(commit).matches()) {
      throw new IllegalArgumentException("Corpus commit must be a full SHA for " + name);
    }
  }

  private static void require(final String value, final String column) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Corpus column is empty: " + column);
    }
  }
}
