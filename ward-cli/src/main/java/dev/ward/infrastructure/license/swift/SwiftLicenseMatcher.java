package dev.ward.infrastructure.license.swift;

import dev.ward.domain.license.LicenseTexts;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Matches a license file against the bundled texts by exact equality after normalization (lower
 * case, one space, no copyright lines, no years, no short title line), or when it begins with the
 * standard header of a bundled text. A near match is never accepted, since a changed clause must
 * not pass for a permissive license.
 */
final class SwiftLicenseMatcher {
  private static final int HEADER_LENGTH = 400;
  private static final int TITLE_LENGTH = 60;

  /** Short texts differ in a clause, so only long standard texts are recognized by their header. */
  private static final int MIN_HEADER_TEXT = 3000;

  private static final Pattern YEARS =
      Pattern.compile("\\b(19|20)\\d{2}(\\s*[-,]\\s*(19|20)\\d{2})*\\b");
  private static final Pattern SPACES = Pattern.compile("\\s+");

  private final LicenseTexts texts;

  SwiftLicenseMatcher(final LicenseTexts texts) {
    this.texts = texts;
  }

  /** Returns the identifier of the bundled text that {@code candidate} is, if any. */
  Optional<String> match(final String candidate) {
    final String normalized = normalize(candidate);
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    final Optional<String> exact =
        texts.ids().stream().filter(id -> normalized.equals(bundled(id))).findFirst();
    return exact.isPresent()
        ? exact
        : texts.ids().stream().filter(id -> startsWithHeader(normalized, bundled(id))).findFirst();
  }

  private String bundled(final String id) {
    return texts.text(id).map(SwiftLicenseMatcher::normalize).orElse("");
  }

  private static boolean startsWithHeader(final String candidate, final String bundled) {
    return bundled.length() >= MIN_HEADER_TEXT
        && candidate.startsWith(bundled.substring(0, Math.min(HEADER_LENGTH, bundled.length())));
  }

  private static String normalize(final String text) {
    final StringBuilder kept = new StringBuilder();
    boolean first = true;
    for (final String line : text.split("\\R")) {
      final String lower = line.strip().toLowerCase(Locale.ROOT);
      if (lower.isEmpty() || lower.startsWith("copyright") || lower.startsWith("(c)")) {
        continue;
      }
      if (first && lower.length() < TITLE_LENGTH && !lower.endsWith(".")) {
        first = false;
        continue;
      }
      first = false;
      kept.append(lower).append(' ');
    }
    return SPACES.matcher(YEARS.matcher(kept).replaceAll("")).replaceAll(" ").strip();
  }
}
