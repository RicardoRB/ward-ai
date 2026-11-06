package dev.ward.infrastructure.rule.catalog;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The status of one catalog row in one language.
 *
 * @param status what the row says
 * @param issue the roadmap issue that implements the rule, such as {@code #283}; empty unless the
 *     status is {@link CatalogStatus#PLANNED}
 * @param reason why the concept does not apply; empty unless the status is {@link
 *     CatalogStatus#NOT_APPLICABLE}
 */
public record LanguageStatus(CatalogStatus status, String issue, String reason) {

  private static final Pattern ISSUE = Pattern.compile("#[1-9][0-9]*");

  /**
   * Validates that a planned status names an issue and a not-applicable status gives a reason.
   *
   * @throws NullPointerException if a field is missing
   * @throws IllegalArgumentException if the status does not match its issue and reason
   */
  public LanguageStatus {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(issue, "issue");
    Objects.requireNonNull(reason, "reason");
    if (status == CatalogStatus.PLANNED && !ISSUE.matcher(issue).matches()) {
      throw new IllegalArgumentException("a planned rule must name its issue as #<number>");
    }
    if (status != CatalogStatus.PLANNED && !issue.isEmpty()) {
      throw new IllegalArgumentException("only a planned rule names an issue");
    }
    if (status == CatalogStatus.NOT_APPLICABLE && reason.isBlank()) {
      throw new IllegalArgumentException("a not-applicable rule must give a reason");
    }
    if (status != CatalogStatus.NOT_APPLICABLE && !reason.isEmpty()) {
      throw new IllegalArgumentException("only a not-applicable rule gives a reason");
    }
  }

  /** Returns the status of a rule that a rule definition implements. */
  public static LanguageStatus implemented() {
    return new LanguageStatus(CatalogStatus.IMPLEMENTED, "", "");
  }

  /** Returns the status of a rule that issue {@code issue} implements. */
  public static LanguageStatus planned(final String issue) {
    return new LanguageStatus(CatalogStatus.PLANNED, issue, "");
  }

  /** Returns the status of a concept that does not apply, for {@code reason}. */
  public static LanguageStatus notApplicable(final String reason) {
    return new LanguageStatus(CatalogStatus.NOT_APPLICABLE, "", reason);
  }
}
