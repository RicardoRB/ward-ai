package dev.ward.analysis.domain.rule.catalog;

import java.util.Locale;
import java.util.Optional;

/** What a catalog row says about one language: the rule exists, is planned, or cannot apply. */
public enum CatalogStatus {
  /** A rule definition ships for the language. */
  IMPLEMENTED,
  /** A later roadmap step implements it; the row names the issue. */
  PLANNED,
  /** The concept has no meaning in the language; the row gives the reason. */
  NOT_APPLICABLE;

  /** Returns the status named {@code key} in a catalog file, such as {@code "not-applicable"}. */
  public static Optional<CatalogStatus> byKey(final String key) {
    for (final CatalogStatus status : values()) {
      if (status.key().equals(key)) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }

  /** Returns the name used in catalog files. */
  public String key() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
