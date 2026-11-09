package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports {@code if a < b: a = b} (or the reverse comparison) as a {@code max} call, and {@code if
 * a > b: a = b} as a {@code min} call, like Pylint's {@code consider-using-max-builtin} and {@code
 * consider-using-min-builtin}. The if has a single assignment and no else or elif. Shortcut: the
 * operands are names or dotted names, matched by their text.
 */
public final class MinMaxBuiltinCheck implements RuleCheck {

  private static final Set<String> IFS = Set.of("if_stmt");
  private static final Pattern IF_ASSIGN =
      Pattern.compile("^if([\\w.]+)(<=?|>=?)([\\w.]+):\\s*([\\w.]+)=([\\w.]+)\\s*$");
  private static final String MAX = "max";
  private static final String MIN = "min";

  private final String builtin;
  private final String message;

  /**
   * Creates the check.
   *
   * @param builtin the builtin the if reduces to, {@code max} or {@code min}
   * @param message the message of each finding
   * @throws IllegalArgumentException if the builtin is neither {@code max} nor {@code min}
   */
  public MinMaxBuiltinCheck(final String builtin, final String message) {
    if (!MAX.equals(builtin) && !MIN.equals(builtin)) {
      throw new IllegalArgumentException("builtin must be max or min: " + builtin);
    }
    this.builtin = builtin;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, IFS)) {
      final String reduced = reducedBuiltin(SyntaxWalk.text(visit.node()));
      if (builtin.equals(reduced)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** The builtin the if reduces to, or {@code null} when it is not a min or max assignment. */
  static String reducedBuiltin(final String text) {
    final Matcher matcher = IF_ASSIGN.matcher(text);
    if (!matcher.matches()) {
      return null;
    }
    String left = matcher.group(1);
    String operator = matcher.group(2);
    final String right = matcher.group(3);
    final String target = matcher.group(4);
    final String value = matcher.group(5);
    if (right.equals(target)) {
      operator = invert(operator);
    } else if (!left.equals(target)) {
      return null;
    }
    if (!value.equals(left) && !value.equals(right)) {
      return null;
    }
    return operator.startsWith("<") ? MAX : MIN;
  }

  private static String invert(final String operator) {
    return switch (operator) {
      case "<" -> ">";
      case "<=" -> ">=";
      case ">" -> "<";
      default -> "<=";
    };
  }
}
