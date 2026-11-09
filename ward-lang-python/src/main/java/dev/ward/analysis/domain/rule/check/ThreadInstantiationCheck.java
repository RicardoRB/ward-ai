package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code Thread(...)} or {@code threading.Thread(...)} call that passes neither a {@code
 * target} keyword nor two positional arguments, like Pylint's {@code bad-thread-instantiation}: the
 * second positional argument of {@code Thread} is the target. Shortcut: the callee is recognised by
 * its name, not by inference.
 */
public final class ThreadInstantiationCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Set<String> NAMES = Set.of("Thread", "threading.Thread");
  private static final String TARGET = "target=";
  private static final int MIN_POSITIONAL = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ThreadInstantiationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final SyntaxNode call = visit.node();
      final String text = SyntaxWalk.text(call);
      final int open = text.indexOf('(');
      if (open > 0 && NAMES.contains(text.substring(0, open)) && isThreadCall(call)) {
        found.add(new RuleMatch(call.span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Whether the first argument list of the call lacks a target and a second positional. */
  static boolean isThreadCall(final SyntaxNode call) {
    final String arguments = argumentText(call);
    if (arguments.contains(TARGET)) {
      return false;
    }
    final List<String> positional = new ArrayList<>();
    for (final String argument : topLevelArguments(arguments)) {
      if (!argument.contains("=")) {
        positional.add(argument);
      }
    }
    return positional.size() < MIN_POSITIONAL;
  }

  /** The text between the first parentheses of the call, without them. */
  private static String argumentText(final SyntaxNode call) {
    final String text = SyntaxWalk.text(call);
    final int open = text.indexOf('(');
    final int close = text.lastIndexOf(')');
    return close > open ? text.substring(open + 1, close) : "";
  }

  /** The comma-separated arguments that are not nested in brackets. */
  static List<String> topLevelArguments(final String arguments) {
    final List<String> parts = new ArrayList<>();
    if (arguments.isEmpty()) {
      return parts;
    }
    int depth = 0;
    int start = 0;
    for (int index = 0; index < arguments.length(); index++) {
      final char c = arguments.charAt(index);
      if (c == '(' || c == '[' || c == '{') {
        depth++;
      } else if (c == ')' || c == ']' || c == '}') {
        depth--;
      } else if (c == ',' && depth == 0) {
        parts.add(arguments.substring(start, index));
        start = index + 1;
      }
    }
    parts.add(arguments.substring(start));
    return parts;
  }
}
