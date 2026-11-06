package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports one kind of mistake in a {@code str.format} call on a string literal, like the pyflakes
 * {@code F52x} rules. A call with a {@code *} or {@code **} argument is not judged, since its
 * arguments are not known.
 */
public final class DotFormatCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** The braces are unbalanced. */
    INVALID,
    /** A keyword argument that no named field uses. */
    UNUSED_NAMED,
    /** A positional argument that no field uses. */
    UNUSED_POSITIONAL,
    /** A field whose argument is not passed. */
    MISSING,
    /** Automatic and manual numbering in one string. */
    MIXED,
    /** A format spec that ends in a type character no format accepts. */
    BAD_CHARACTER
  }

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final String FORMAT_CALL = ".format(";
  private static final String TYPE_CHARACTERS = "bcdeEfFgGnosxX%";
  private static final String STAR = "*";
  private static final String DOUBLE_STAR = "**";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public DotFormatCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final Optional<Call> call = call(SyntaxWalk.text(visit.node()));
      if (call.isPresent() && violates(call.get())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** The format and the arguments of a {@code "...".format(...)} call, if the text is one. */
  private static Optional<Call> call(final String text) {
    final int quote = openingQuote(text);
    if (quote < 0) {
      return Optional.empty();
    }
    final char kind = text.charAt(quote);
    final int close = text.indexOf(kind, quote + 1);
    if (close < 0 || !text.startsWith(FORMAT_CALL, close + 1) || !text.endsWith(")")) {
      return Optional.empty();
    }
    final int argsStart = close + 1 + FORMAT_CALL.length();
    final String args = text.substring(argsStart, text.length() - 1);
    return arguments(args)
        .map(parsed -> new Call(DotFormatString.parse(text.substring(quote + 1, close)), parsed));
  }

  private static int openingQuote(final String text) {
    for (int index = 0; index < text.length(); index++) {
      final char current = text.charAt(index);
      if (current == '"' || current == '\'') {
        return index;
      }
      if ("rRbBuU".indexOf(current) < 0) {
        return -1;
      }
    }
    return -1;
  }

  private static Optional<Arguments> arguments(final String args) {
    final List<String> items = args.isEmpty() ? List.of() : PercentArgument.split(args, ',');
    final List<String> keywords = new ArrayList<>();
    int positional = 0;
    for (final String item : items) {
      if (item.startsWith(STAR) || item.startsWith(DOUBLE_STAR)) {
        return Optional.empty();
      }
      final int equals = item.indexOf('=');
      if (equals > 0 && !item.startsWith("=", equals + 1) && isName(item.substring(0, equals))) {
        keywords.add(item.substring(0, equals));
      } else {
        positional++;
      }
    }
    return Optional.of(new Arguments(positional, keywords));
  }

  private static boolean isName(final String text) {
    return !text.isEmpty()
        && Character.isJavaIdentifierStart(text.charAt(0))
        && text.chars().allMatch(Character::isJavaIdentifierPart);
  }

  private boolean violates(final Call call) {
    final DotFormatString format = call.format();
    final Arguments arguments = call.arguments();
    return switch (problem) {
      case INVALID -> format.invalid();
      case UNUSED_NAMED ->
          arguments.keywords().stream().anyMatch(key -> !format.names().contains(key));
      case UNUSED_POSITIONAL -> arguments.positional() > usedPositional(format);
      case MISSING -> missing(format, arguments);
      case MIXED -> format.automatic() > 0 && !format.manual().isEmpty();
      case BAD_CHARACTER -> format.specs().stream().anyMatch(DotFormatCheck::badType);
    };
  }

  private static boolean badType(final String spec) {
    return !spec.isEmpty()
        && Character.isLetter(spec.charAt(spec.length() - 1))
        && TYPE_CHARACTERS.indexOf(spec.charAt(spec.length() - 1)) < 0;
  }

  private static int usedPositional(final DotFormatString format) {
    final int highest = format.manual().stream().mapToInt(Integer::intValue).max().orElse(-1);
    return Math.max(format.automatic(), highest + 1);
  }

  private static boolean missing(final DotFormatString format, final Arguments arguments) {
    final int highest = format.manual().stream().mapToInt(Integer::intValue).max().orElse(-1);
    return format.automatic() > arguments.positional()
        || highest >= arguments.positional()
        || !arguments.keywords().containsAll(format.names());
  }

  /** The string literal's format and the call's arguments. */
  private record Call(DotFormatString format, Arguments arguments) {}

  /** The count of positional arguments and the names of the keyword arguments. */
  private record Arguments(int positional, List<String> keywords) {}
}
