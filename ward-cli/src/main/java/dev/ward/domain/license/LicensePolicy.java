package dev.ward.domain.license;

import dev.ward.domain.config.LicenseSettings;
import java.util.List;
import java.util.Locale;

/**
 * Applies the {@link LicenseSettings} of a {@code license} check to license expressions: a deny
 * list or an allow list, with {@code WITH} exceptions that waive a denied license.
 */
public final class LicensePolicy {
  private final LicenseSettings settings;

  /** Creates the policy of {@code settings}. */
  public LicensePolicy(final LicenseSettings settings) {
    this.settings = settings;
  }

  /** Decides {@code expression}: it passes when some choice satisfies the policy. */
  public LicenseVerdict check(final LicenseExpression expression) {
    return switch (expression) {
      case LicenseExpression.Term term ->
          settings.mode() == LicenseSettings.Mode.DENY ? denyTerm(term) : allowTerm(term);
      case LicenseExpression.And and -> both(and);
      case LicenseExpression.Or or -> either(or);
    };
  }

  private LicenseVerdict both(final LicenseExpression.And and) {
    final LicenseVerdict left = check(and.left());
    return left.ok() ? check(and.right()) : left;
  }

  private LicenseVerdict either(final LicenseExpression.Or or) {
    final LicenseVerdict left = check(or.left());
    if (left.ok()) {
      return left;
    }
    final LicenseVerdict right = check(or.right());
    return right.ok() ? right : left;
  }

  private LicenseVerdict denyTerm(final LicenseExpression.Term term) {
    if (term.exception() != null && listed(settings.exceptions(), term.exception())) {
      return LicenseVerdict.passed();
    }
    return settings.patterns().stream()
        .filter(pattern -> matches(pattern, term.id()))
        .findFirst()
        .map(
            pattern ->
                new LicenseVerdict(LicenseVerdict.Outcome.DENIED, "deny " + pattern, term.id()))
        .orElseGet(LicenseVerdict::passed);
  }

  private LicenseVerdict allowTerm(final LicenseExpression.Term term) {
    return listed(settings.patterns(), term.id())
        ? LicenseVerdict.passed()
        : new LicenseVerdict(
            LicenseVerdict.Outcome.NOT_ALLOWED, "allow " + settings.patterns(), term.id());
  }

  private static boolean listed(final List<String> entries, final String id) {
    return entries.stream().anyMatch(entry -> matches(entry, id));
  }

  /** Matches an identifier exactly, or by prefix when the pattern ends in {@code *}. */
  private static boolean matches(final String pattern, final String id) {
    final String wanted = pattern.toLowerCase(Locale.ROOT);
    final String actual = id.toLowerCase(Locale.ROOT);
    return wanted.endsWith("*")
        ? actual.startsWith(wanted.substring(0, wanted.length() - 1))
        : wanted.equals(actual);
  }
}
