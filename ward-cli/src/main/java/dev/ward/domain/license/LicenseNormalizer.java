package dev.ward.domain.license;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns a declared license into an SPDX expression using the bundled {@link LicenseCatalog}. A
 * declared text that cannot be mapped stays unknown: the result is empty, never a guess.
 */
public final class LicenseNormalizer {
  private static final Pattern LICENSE_REF =
      Pattern.compile("(DocumentRef-[A-Za-z0-9.-]+:)?LicenseRef-[A-Za-z0-9.-]+");
  private static final String ONLY = "-only";
  private static final String OR_LATER = "-or-later";

  private final LicenseCatalog catalog;

  /** Creates the normalizer over {@code catalog}. */
  public LicenseNormalizer(final LicenseCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * Returns the expression {@code declared} stands for, the entries being alternatives, or empty
   * when any entry cannot be mapped.
   */
  public Optional<LicenseExpression> normalize(final DeclaredLicense declared) {
    final List<Optional<LicenseExpression>> entries =
        declared.entries().stream().map(this::entry).toList();
    if (entries.stream().anyMatch(Optional::isEmpty)) {
      return Optional.empty();
    }
    return entries.stream().map(Optional::get).reduce(LicenseExpression.Or::new);
  }

  private Optional<LicenseExpression> entry(final DeclaredLicense.Entry entry) {
    if (!entry.url().isEmpty()) {
      final Optional<LicenseExpression> byUrl =
          catalog.alias(entry.url()).map(LicenseNormalizer::term);
      if (byUrl.isPresent()) {
        return byUrl;
      }
    }
    if (entry.name().isEmpty()) {
      return Optional.empty();
    }
    final Optional<LicenseExpression> parsed =
        LicenseExpressionParser.parse(entry.name()).flatMap(this::canonical);
    return parsed.isPresent() ? parsed : catalog.alias(entry.name()).map(LicenseNormalizer::term);
  }

  private static LicenseExpression term(final String id) {
    return new LicenseExpression.Term(id, null);
  }

  private Optional<LicenseExpression> canonical(final LicenseExpression expression) {
    return switch (expression) {
      case LicenseExpression.Term term -> canonicalTerm(term);
      case LicenseExpression.And and ->
          pair(and.left(), and.right()).map(p -> new LicenseExpression.And(p[0], p[1]));
      case LicenseExpression.Or or ->
          pair(or.left(), or.right()).map(p -> new LicenseExpression.Or(p[0], p[1]));
    };
  }

  private Optional<LicenseExpression[]> pair(
      final LicenseExpression left, final LicenseExpression right) {
    final Optional<LicenseExpression> first = canonical(left);
    final Optional<LicenseExpression> second = canonical(right);
    return first.isPresent() && second.isPresent()
        ? Optional.of(new LicenseExpression[] {first.get(), second.get()})
        : Optional.empty();
  }

  private Optional<LicenseExpression> canonicalTerm(final LicenseExpression.Term term) {
    final Optional<String> id = identifier(term.id());
    if (id.isEmpty()) {
      return Optional.empty();
    }
    if (term.exception() == null) {
      return Optional.of(term(id.get()));
    }
    return catalog
        .id(term.exception())
        .map(exception -> new LicenseExpression.Term(id.get(), exception));
  }

  /** Maps one identifier, applying a trailing {@code +} to the {@code -only} form. */
  private Optional<String> identifier(final String written) {
    if (LICENSE_REF.matcher(written).matches()) {
      return Optional.of(written);
    }
    if (!written.endsWith("+")) {
      return catalog.id(written);
    }
    final Optional<String> base = catalog.id(written.substring(0, written.length() - 1));
    return base.filter(id -> id.endsWith(ONLY))
        .map(id -> id.substring(0, id.length() - ONLY.length()) + OR_LATER);
  }
}
