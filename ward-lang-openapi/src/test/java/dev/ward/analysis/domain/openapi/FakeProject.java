package dev.ward.analysis.domain.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** An in-memory project that records which files the loader reads. */
final class FakeProject {

  /** The entry file of the loaded documents. */
  static final String ENTRY = "openapi.yaml";

  private final Map<String, String> files;
  private final List<String> reads = new ArrayList<>();

  FakeProject(final Map<String, String> files) {
    this.files = Map.copyOf(files);
  }

  /** The paths read so far, in order. */
  List<String> reads() {
    return reads;
  }

  LoadResult load(final String entry) {
    return OpenApiDocuments.load(
        entry,
        path -> {
          reads.add(path);
          return Optional.ofNullable(files.get(path));
        });
  }

  /** Loads {@code openapi.yaml} and asserts that it loaded. */
  OpenApiDocument loaded() {
    final LoadResult result = load(ENTRY);
    assertThat(result.errors()).isEmpty();
    return result.document().orElseThrow();
  }

  static FakeProject of(final String spec) {
    return new FakeProject(Map.of(ENTRY, spec));
  }

  static FakeProject of(final String spec, final String otherPath, final String other) {
    return new FakeProject(Map.of(ENTRY, spec, otherPath, other));
  }
}
