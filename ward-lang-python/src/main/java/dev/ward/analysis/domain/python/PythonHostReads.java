package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.python.PythonTokens.Range;
import java.util.List;
import java.util.Set;

/**
 * Reads hosts: a {@code .netloc} or {@code .hostname} of a value made from a parameter, alone, as
 * a rejection, or compared.
 */
final class PythonHostReads {
  private static final Set<String> HOSTS = Set.of("netloc", "hostname");
  private static final Set<String> OPERATORS =
      Set.of("==", "!=", "in", "is", "<", ">", "<=", ">=", "not", "and", "or", "+");
  private static final String NOT = "not";

  private PythonHostReads() {}

  /** A host read of a value made from the parameter, compared for equality or membership. */
  static boolean comparesHost(final List<String> tokens, final Range part, final Set<String> made) {
    final boolean negative = NOT.equals(tokens.get(part.from()));
    final boolean compares =
        PythonTokens.has(tokens, part, "==") || PythonTokens.has(tokens, part, "in") && !hasNotIn(
            tokens,
            part
        );
    return !negative && compares && readsHost(tokens, part, made);
  }

  static boolean hasNotIn(final List<String> tokens, final Range part) {
    for (int index = part.from() + 1; index < part.to(); index++) {
      if ("in".equals(tokens.get(index)) && NOT.equals(tokens.get(index - 1))) {
        return true;
      }
    }
    return false;
  }

  static boolean rejection(final List<String> tokens, final Range part, final Set<String> made) {
    if (!readsHost(tokens, part, made)) {
      return false;
    }
    return PythonTokens.has(tokens, part, "!=")
        || hasNotIn(tokens, part)
        || isHostAlone(tokens, part);
  }

  private static boolean isHostAlone(final List<String> tokens, final Range part) {
    for (int index = part.from(); index < part.to(); index++) {
      if (OPERATORS.contains(tokens.get(index))) {
        return false;
      }
    }
    return HOSTS.contains(tokens.get(part.to() - 1));
  }

  /** Whether the range reads {@code .netloc} or {@code .hostname} of a value made from the parameter. */
  private static boolean readsHost(
      final List<String> tokens,
      final Range part,
      final Set<String> made
  ) {
    for (int index = part.from() + 1; index < part.to(); index++) {
      if (HOSTS.contains(tokens.get(index))
          && ".".equals(tokens.get(index - 1))
          && ownedBy(tokens, part.from(), index - 1, made)) {
        return true;
      }
    }
    return false;
  }

  /** Whether the chain of names, calls and indexes before the dot is made from the parameter. */
  private static boolean ownedBy(
      final List<String> tokens,
      final int from,
      final int dot,
      final Set<String> made
  ) {
    int index = dot - 1;
    while (index >= from) {
      final String token = tokens.get(index);
      if (")".equals(token) || "]".equals(token)) {
        index = openOf(tokens, index, from) - 1;
      } else if (".".equals(token)
          || token.matches("[A-Za-z_]\\w*") && !OPERATORS.contains(token)) {
        index--;
      } else {
        break;
      }
    }
    return PythonTokens.mentions(tokens, new Range(index + 1, dot), made);
  }

  private static int openOf(final List<String> tokens, final int close, final int from) {
    int depth = 0;
    for (int index = close; index >= from; index--) {
      final String token = tokens.get(index);
      depth += ")".equals(token) || "]".equals(token) ? 1 : 0;
      depth -= "(".equals(token) || "[".equals(token) ? 1 : 0;
      if (depth == 0) {
        return index;
      }
    }
    return from;
  }
}
