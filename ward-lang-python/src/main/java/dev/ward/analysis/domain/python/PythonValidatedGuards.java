package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.python.PythonTokens.Call;
import dev.ward.analysis.domain.python.PythonTokens.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds the calls of a condition that a branch can rely on. Inside the first branch of {@code if a
 * and f(x):} the call {@code f(x)} was true, and past {@code if a or not f(x): return} it was true
 * as well. Whether {@code f} proves anything is not decided here; the taint analysis reads the body
 * of {@code f} for that.
 *
 * <p>shortcut: the positional arguments before the first keyword or star argument, and only a
 * condition that is a plain chain of {@code and}, or of {@code or}; upgrade when a project guards
 * with a mix of them.
 */
final class PythonValidatedGuards {
  private PythonValidatedGuards() {}

  /** The calls that are true inside the branch the condition guards. */
  static List<Call> inside(final List<String> tokens) {
    final Range all = new Range(0, tokens.size());
    if (tokens.isEmpty() || PythonTokens.has(tokens, all, "or")) {
      return List.of();
    }
    final List<Call> calls = new ArrayList<>();
    for (final Range conjunct : PythonTokens.split(tokens, all, "and")) {
      PythonTokens.call(tokens, conjunct).ifPresent(calls::add);
    }
    return calls;
  }

  /** The calls that are true past a branch that exits when the condition holds. */
  static List<Call> past(final List<String> tokens) {
    final Range all = new Range(0, tokens.size());
    if (tokens.isEmpty() || PythonTokens.has(tokens, all, "and")) {
      return List.of();
    }
    final List<Call> calls = new ArrayList<>();
    for (final Range disjunct : PythonTokens.split(tokens, all, "or")) {
      negated(tokens, disjunct).ifPresent(calls::add);
    }
    return calls;
  }

  private static Optional<Call> negated(final List<String> tokens, final Range disjunct) {
    return disjunct.to() - disjunct.from() > 1 && "not".equals(tokens.get(disjunct.from()))
        ? PythonTokens.call(tokens, new Range(disjunct.from() + 1, disjunct.to()))
        : Optional.empty();
  }

  /** How many arguments of the call come before the first keyword or star argument. */
  static int positional(final List<String> tokens, final Call call) {
    int count = 0;
    for (final Range argument : call.arguments()) {
      if (tokens.get(argument.from()).startsWith("*") || PythonTokens.has(tokens, argument, "=")) {
        break;
      }
      count++;
    }
    return count;
  }
}
