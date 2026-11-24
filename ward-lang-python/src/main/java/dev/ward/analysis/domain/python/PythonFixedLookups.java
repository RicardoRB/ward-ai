package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the assignments {@code name = TABLE.get(key)} and {@code name = TABLE[key]} whose table is
 * a collection that the program fixes. Whatever the key is, the value is one of the members of the
 * table, so the name holds a value of the program and not one that the key carries.
 *
 * <p>shortcut: only a bare name assigned the whole lookup, and {@code get} with the one key
 * of a mapping, no default; upgrade when a project looks a value up inside a larger expression.
 */
final class PythonFixedLookups {
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
  private static final String ASSIGN = "=";
  private static final String GET = "get";
  private static final String OPEN_CALL = "(";
  private static final String CLOSE_CALL = ")";
  private static final String OPEN_INDEX = "[";
  private static final String CLOSE_INDEX = "]";
  private static final int LOOKUP_START = 3;

  private PythonFixedLookups() {}

  /** The name that the statement assigns a member of a fixed table, when it does. */
  static Optional<String> target(final SyntaxNode statement, final Set<String> fixed) {
    final List<SyntaxNode> parts = statement.children();
    if (!"expr_stmt".equals(statement.kind())
        || parts.size() != PythonSyntax.THREE_PARTS
        || !PythonSyntax.isToken(parts.get(1), ASSIGN)) {
      return Optional.empty();
    }
    final List<String> target = PythonLiteralCollections.words(parts.get(0));
    final List<String> value = PythonLiteralCollections.words(parts.get(2));
    return target.size() == 1
        && IDENTIFIER.matcher(target.get(0)).matches()
        && isLookup(value, fixed)
        ? Optional.of(target.get(0))
        : Optional.empty();
  }

  private static boolean isLookup(final List<String> value, final Set<String> fixed) {
    if (value.size() <= LOOKUP_START || !fixed.contains(value.get(0))) {
      return false;
    }
    final String last = value.get(value.size() - 1);
    if (OPEN_INDEX.equals(value.get(1))) {
      return CLOSE_INDEX.equals(last) && closesAtEnd(value, 1, OPEN_INDEX, CLOSE_INDEX);
    }
    return ".".equals(value.get(1))
        && GET.equals(value.get(2))
        && OPEN_CALL.equals(value.get(LOOKUP_START))
        && CLOSE_CALL.equals(last)
        && closesAtEnd(value, LOOKUP_START, OPEN_CALL, CLOSE_CALL)
        && !hasDefault(value);
  }

  /** A second argument is a default that the key itself may be, so it is not a member. */
  private static boolean hasDefault(final List<String> value) {
    int depth = 0;
    for (int index = LOOKUP_START; index < value.size(); index++) {
      final String token = value.get(index);
      depth += "([{".contains(token) && token.length() == 1 ? 1 : 0;
      depth -= ")]}".contains(token) && token.length() == 1 ? 1 : 0;
      if (depth == 1 && ",".equals(token)) {
        return true;
      }
    }
    return false;
  }

  /** Whether the bracket at {@code open} is the one that the last token closes. */
  private static boolean closesAtEnd(
      final List<String> value,
      final int open,
      final String opener,
      final String closer
  ) {
    int depth = 0;
    for (int index = open; index < value.size(); index++) {
      final String token = value.get(index);
      depth += opener.equals(token) ? 1 : 0;
      depth -= closer.equals(token) ? 1 : 0;
      if (depth == 0) {
        return index == value.size() - 1;
      }
    }
    return false;
  }
}
