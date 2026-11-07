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
 * Reports a call that takes a {@code strict} argument on the project's Python version but does not
 * pass one, like {@code B911} ({@code itertools.batched}, Python 3.13) and {@code B912} ({@code
 * map} with several iterables, Python 3.14). The call is reported only when the target version
 * reaches the one that introduced {@code strict}.
 */
public final class StrictCallCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final String STRICT = "strict=";
  private static final String PREFIX = "py3";

  private final Rule rule;
  private final int targetMinor;
  private final String message;

  /**
   * What makes a call need {@code strict}.
   *
   * @param call a pattern the call text starts with
   * @param minimumMinor the Python 3 minor version that added {@code strict}
   * @param minimumArguments the arguments a call needs before it is judged; a starred argument
   *     always counts
   */
  public record Rule(Pattern call, int minimumMinor, int minimumArguments) {}

  /**
   * Creates the check.
   *
   * @param rule what makes a call need {@code strict}
   * @param target the project's Python version, such as {@code py313}
   * @param message the message of each finding
   * @throws IllegalArgumentException if the target is not of the form {@code py3NN}
   */
  public StrictCallCheck(final Rule rule, final String target, final String message) {
    this.rule = Objects.requireNonNull(rule, "rule");
    this.targetMinor = minor(target);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    if (targetMinor < rule.minimumMinor()) {
      return found;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      if (needsStrict(SyntaxWalk.text(visit.node()))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private boolean needsStrict(final String text) {
    final Matcher call = rule.call().matcher(text);
    if (!call.find() || !text.endsWith(")") || text.contains(STRICT)) {
      return false;
    }
    final List<String> arguments =
        PercentArgument.split(text.substring(call.end(), text.length() - 1), ',');
    return arguments.size() >= rule.minimumArguments()
        || arguments.stream().anyMatch(argument -> argument.startsWith("*"));
  }

  private static int minor(final String target) {
    if (!target.startsWith(PREFIX)) {
      throw new IllegalArgumentException("target version " + target + " is not of the form py3NN");
    }
    return Integer.parseInt(target.substring(PREFIX.length()));
  }
}
