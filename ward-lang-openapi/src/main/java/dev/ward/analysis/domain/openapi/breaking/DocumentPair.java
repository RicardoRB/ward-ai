package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The two documents a comparison reads and the day it is made.
 *
 * @param base the document at the baseline revision
 * @param revision the working-tree document
 * @param today the day that sunset dates are measured against
 */
public record DocumentPair(OpenApiDocument base, OpenApiDocument revision, LocalDate today) {

  /**
   * Validates the parts.
   *
   * @throws NullPointerException if a part is missing
   */
  public DocumentPair {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(revision, "revision");
    Objects.requireNonNull(today, "today");
  }
}
