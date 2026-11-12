package dev.ward.domain.license;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses SPDX license expressions: identifiers with an optional {@code +}, {@code WITH}, {@code
 * AND}, {@code OR} and parentheses, with {@code AND} binding tighter than {@code OR}. Operators are
 * case-insensitive. Any text that is not a well-formed expression is rejected, never guessed at.
 */
public final class LicenseExpressionParser {
  private static final Pattern TOKEN = Pattern.compile("\\(|\\)|[^\\s()]+");
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.:_-]*\\+?");
  private static final String OPEN = "(";
  private static final String CLOSE = ")";

  private final List<String> tokens;
  private int position;

  private LicenseExpressionParser(final List<String> tokens) {
    this.tokens = tokens;
  }

  /** Returns the expression {@code text} spells, or empty when it is not a valid expression. */
  public static Optional<LicenseExpression> parse(final String text) {
    final List<String> tokens = new ArrayList<>();
    final java.util.regex.Matcher matcher = TOKEN.matcher(text.trim());
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    if (tokens.isEmpty()) {
      return Optional.empty();
    }
    final LicenseExpressionParser parser = new LicenseExpressionParser(tokens);
    return parser.or().filter(expression -> parser.position == tokens.size());
  }

  private Optional<LicenseExpression> or() {
    final Optional<LicenseExpression> left = and();
    if (left.isEmpty() || !peekIs("OR")) {
      return left;
    }
    position++;
    return or().map(right -> new LicenseExpression.Or(left.get(), right));
  }

  private Optional<LicenseExpression> and() {
    final Optional<LicenseExpression> left = with();
    if (left.isEmpty() || !peekIs("AND")) {
      return left;
    }
    position++;
    return and().map(right -> new LicenseExpression.And(left.get(), right));
  }

  private Optional<LicenseExpression> with() {
    final Optional<LicenseExpression> primary = primary();
    if (primary.isPresent()
        && primary.get() instanceof LicenseExpression.Term term
        && peekIs("WITH")) {
      position++;
      return identifier().map(exception -> new LicenseExpression.Term(term.id(), exception));
    }
    return primary;
  }

  private Optional<LicenseExpression> primary() {
    if (position < tokens.size() && OPEN.equals(tokens.get(position))) {
      position++;
      final Optional<LicenseExpression> inner = or();
      if (inner.isEmpty() || position >= tokens.size() || !CLOSE.equals(tokens.get(position))) {
        return Optional.empty();
      }
      position++;
      return inner;
    }
    return identifier().map(id -> new LicenseExpression.Term(id, null));
  }

  private Optional<String> identifier() {
    if (position >= tokens.size()) {
      return Optional.empty();
    }
    final String token = tokens.get(position);
    if (!IDENTIFIER.matcher(token).matches() || isOperator(token)) {
      return Optional.empty();
    }
    position++;
    return Optional.of(token);
  }

  private boolean peekIs(final String operator) {
    return position < tokens.size() && tokens.get(position).equalsIgnoreCase(operator);
  }

  private static boolean isOperator(final String token) {
    final String upper = token.toUpperCase(Locale.ROOT);
    return "AND".equals(upper) || "OR".equals(upper) || "WITH".equals(upper);
  }
}
