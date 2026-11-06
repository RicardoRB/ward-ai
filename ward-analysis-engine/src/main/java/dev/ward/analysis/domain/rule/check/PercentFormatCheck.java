package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports one kind of mistake in a {@code %} format of a string literal with a literal right
 * operand, like the pyflakes {@code F50x} rules. Each rule names its problem; the check reads the
 * literal and the operand and reports where the {@code %} expression starts.
 */
public final class PercentFormatCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** The format ends inside a conversion. */
    INVALID,
    /** A conversion character is not supported. */
    UNSUPPORTED,
    /** Named and positional conversions are mixed. */
    MIXED,
    /** Named conversions with a tuple operand. */
    EXPECTED_MAPPING,
    /** A {@code *} width with a dictionary operand. */
    STAR_MAPPING,
    /** Positional conversions and a tuple of a different length. */
    COUNT,
    /** Dictionary keys that no named conversion uses. */
    UNUSED_KEYS,
    /** Named conversions whose key the dictionary does not have. */
    MISSING_KEYS,
    /** A numeric conversion with a string literal operand. */
    TYPE_MISMATCH
  }

  private static final Set<String> EXPRESSION = Set.of("expr");
  private static final int OPERANDS = 3;
  private static final int OPERATOR_INDEX = 1;
  private static final String OPERATOR = "%";
  private static final String PREFIX_CHARS = "rRbBuU";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public PercentFormatCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, EXPRESSION)) {
      operation(visit.node())
          .filter(this::violates)
          .ifPresent(operation -> found.add(new RuleMatch(visit.node().span(), message)));
    }
    return List.copyOf(found);
  }

  private Optional<Operation> operation(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.size() != OPERANDS
        || !OPERATOR.equals(SyntaxWalk.text(children.get(OPERATOR_INDEX)))) {
      return Optional.empty();
    }
    return literalContent(SyntaxWalk.text(children.get(0)))
        .map(
            content ->
                new Operation(
                    PercentFormatString.parse(content),
                    PercentArgument.of(SyntaxWalk.text(children.get(2)))));
  }

  /** The text between the quotes of one string literal, if the text is exactly one. */
  private static Optional<String> literalContent(final String text) {
    int start = 0;
    while (start < text.length() && PREFIX_CHARS.indexOf(text.charAt(start)) >= 0) {
      start++;
    }
    if (start >= text.length()) {
      return Optional.empty();
    }
    final char quote = text.charAt(start);
    final int end = text.length() - 1;
    if ((quote != '"' && quote != '\'') || end <= start || text.charAt(end) != quote) {
      return Optional.empty();
    }
    final String content = text.substring(start + 1, end);
    return content.indexOf(quote) >= 0 ? Optional.empty() : Optional.of(content);
  }

  private boolean violates(final Operation operation) {
    final PercentFormatString format = operation.format();
    final PercentArgument argument = operation.argument();
    return switch (problem) {
      case INVALID -> format.incomplete();
      case UNSUPPORTED -> format.unsupported().isPresent();
      case MIXED -> mixed(format);
      case EXPECTED_MAPPING -> expectedMapping(format, argument);
      case STAR_MAPPING -> starMapping(format, argument);
      case COUNT -> count(format, argument);
      case UNUSED_KEYS -> unusedKeys(operation);
      case MISSING_KEYS -> missingKeys(operation);
      case TYPE_MISMATCH -> typeMismatch(format, argument);
    };
  }

  private static boolean expectedMapping(
      final PercentFormatString format, final PercentArgument argument) {
    return hasNamed(format) && argument.kind() == PercentArgument.Kind.TUPLE;
  }

  private static boolean starMapping(
      final PercentFormatString format, final PercentArgument argument) {
    return format.star() && argument.kind() == PercentArgument.Kind.DICT;
  }

  private static boolean count(final PercentFormatString format, final PercentArgument argument) {
    return !hasNamed(format) && countMismatch(format, argument);
  }

  private static boolean typeMismatch(
      final PercentFormatString format, final PercentArgument argument) {
    return format.numeric() && argument.kind() == PercentArgument.Kind.STRING;
  }

  private static boolean hasNamed(final PercentFormatString format) {
    return !format.named().isEmpty();
  }

  private static boolean mixed(final PercentFormatString format) {
    return hasNamed(format) && format.positional() > 0;
  }

  private static boolean countMismatch(
      final PercentFormatString format, final PercentArgument argument) {
    return argument.kind() == PercentArgument.Kind.TUPLE && format.positional() != argument.count();
  }

  private static boolean unusedKeys(final Operation operation) {
    final List<String> named = operation.format().named();
    return operation
        .argument()
        .keys()
        .map(keys -> keys.stream().anyMatch(key -> !named.contains(key)))
        .orElse(false);
  }

  private static boolean missingKeys(final Operation operation) {
    return hasNamed(operation.format())
        && operation
            .argument()
            .keys()
            .map(keys -> !keys.containsAll(operation.format().named()))
            .orElse(false);
  }

  /** One {@code %} expression: its format and its right operand. */
  private record Operation(PercentFormatString format, PercentArgument argument) {}
}
