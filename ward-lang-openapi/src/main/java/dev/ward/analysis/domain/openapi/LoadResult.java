package dev.ward.analysis.domain.openapi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of loading a document: the document, or the errors that made it unusable. No rule
 * runs on a document that did not load.
 *
 * @param document the loaded document; empty when there are errors
 * @param errors the problems found, in the order they were met; empty when the document loaded
 */
public record LoadResult(Optional<OpenApiDocument> document, List<LoadError> errors) {

  /**
   * Copies the errors.
   *
   * @throws NullPointerException if a part is missing
   */
  public LoadResult {
    Objects.requireNonNull(document, "document");
    errors = List.copyOf(errors);
  }

  /** Tells whether the document loaded. */
  public boolean loaded() {
    return document.isPresent();
  }
}
