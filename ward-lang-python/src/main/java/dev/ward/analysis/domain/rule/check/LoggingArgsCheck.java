package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a {@code logging} call whose argument count does not match the {@code %} placeholders of
 * its message, like pylint {@code logging-too-many-args} and {@code logging-too-few-args}. Only the
 * module-level level functions are judged, and a call with a star argument or named placeholders is
 * not.
 */
public final class LoggingArgsCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** More arguments than placeholders. */
    TOO_MANY,
    /** Fewer arguments than placeholders. */
    TOO_FEW
  }

  private static final Pattern CALL =
      Pattern.compile("^logging\\.(debug|info|warning|warn|error|exception|critical)\\(");
  private static final Set<String> CALLS = Set.of("atom_expr");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public LoggingArgsCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final Optional<Counts> counts = counts(SyntaxWalk.text(visit.node()));
      if (counts.isPresent() && violates(counts.get())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private Optional<Counts> counts(final String text) {
    final Matcher call = CALL.matcher(text);
    if (!call.find()) {
      return Optional.empty();
    }
    final int quote = call.end();
    if (quote >= text.length() || (text.charAt(quote) != '"' && text.charAt(quote) != '\'')) {
      return Optional.empty();
    }
    final int close = text.indexOf(text.charAt(quote), quote + 1);
    if (close < 0 || close + 1 >= text.length()) {
      return Optional.empty();
    }
    final String rest = text.substring(close + 1);
    if (!rest.endsWith(")")) {
      return Optional.empty();
    }
    final String args = rest.startsWith(",") ? rest.substring(1, rest.length() - 1) : "";
    return argumentCount(args)
        .map(
            count ->
                new Counts(PercentFormatString.parse(text.substring(quote + 1, close)), count));
  }

  private static Optional<Integer> argumentCount(final String args) {
    if (args.isEmpty()) {
      return Optional.of(0);
    }
    int count = 0;
    for (final String item : PercentArgument.split(args, ',')) {
      if (item.startsWith("*")) {
        return Optional.empty();
      }
      if (!isKeyword(item)) {
        count++;
      }
    }
    return Optional.of(count);
  }

  private static boolean isKeyword(final String item) {
    final int equals = item.indexOf('=');
    return equals > 0
        && !item.startsWith("=", equals + 1)
        && item.substring(0, equals).matches("[A-Za-z_]\\w*");
  }

  private boolean violates(final Counts counts) {
    if (!counts.format().named().isEmpty()) {
      return false;
    }
    return switch (problem) {
      case TOO_MANY -> counts.arguments() > counts.format().positional();
      case TOO_FEW -> counts.arguments() < counts.format().positional();
    };
  }

  /** The placeholders of the message and the number of arguments after it. */
  private record Counts(PercentFormatString format, int arguments) {}
}
