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
    if (children.size() != 3 || !OPERATOR.equals(SyntaxWalk.text(children.get(1)))) {
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
    final boolean named = !format.named().isEmpty();
    return switch (problem) {
      case INVALID -> format.incomplete();
      case UNSUPPORTED -> format.unsupported().isPresent();
      case MIXED -> named && format.positional() > 0;
      case EXPECTED_MAPPING -> named && argument.kind() == PercentArgument.Kind.TUPLE;
      case STAR_MAPPING -> format.star() && argument.kind() == PercentArgument.Kind.DICT;
      case COUNT -> !named && countMismatch(format, argument);
      case UNUSED_KEYS -> named && unusedKeys(operation);
      case MISSING_KEYS -> named && missingKeys(operation);
      case TYPE_MISMATCH -> format.numeric() && argument.kind() == PercentArgument.Kind.STRING;
    };
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
    final List<String> named = operation.format().named();
    return operation.argument().keys().map(keys -> !keys.containsAll(named)).orElse(false);
  }

  /** One {@code %} expression: its format and its right operand. */
  private record Operation(PercentFormatString format, PercentArgument argument) {}
}
