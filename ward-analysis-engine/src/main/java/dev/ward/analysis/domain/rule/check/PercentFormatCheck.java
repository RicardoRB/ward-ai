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
    TYPE_MISMATCH,
    /** Several positional conversions with a dictionary operand. */
    EXPECTED_SEQUENCE
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
          .filter(operation -> operation.violates(problem))
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

  /** One {@code %} expression: its format and its right operand. */
  private record Operation(PercentFormatString format, PercentArgument argument) {

    boolean violates(final Problem kind) {
      return switch (kind) {
        case INVALID -> format.incomplete();
        case UNSUPPORTED -> format.unsupported().isPresent();
        case STAR_MAPPING -> starMapping();
        case TYPE_MISMATCH -> typeMismatch();
        case EXPECTED_SEQUENCE -> expectedSequence();
        default -> violatesWithArguments(kind);
      };
    }

    private boolean violatesWithArguments(final Problem kind) {
      return switch (kind) {
        case MIXED -> mixed();
        case EXPECTED_MAPPING -> expectedMapping();
        case COUNT -> count();
        case UNUSED_KEYS -> unusedKeys();
        default -> missingKeys();
      };
    }

    private boolean mixed() {
      return hasNamed() && format.positional() > 0;
    }

    private boolean expectedMapping() {
      return hasNamed() && argument.kind() == PercentArgument.Kind.TUPLE;
    }

    private boolean starMapping() {
      return format.star() && argument.kind() == PercentArgument.Kind.DICT;
    }

    private boolean count() {
      return !hasNamed()
          && argument.kind() == PercentArgument.Kind.TUPLE
          && format.positional() != argument.count();
    }

    private boolean expectedSequence() {
      return !hasNamed() && argument.kind() == PercentArgument.Kind.DICT && format.positional() > 1;
    }

    private boolean typeMismatch() {
      return format.numeric() && argument.kind() == PercentArgument.Kind.STRING;
    }

    private boolean hasNamed() {
      return !format.named().isEmpty();
    }

    private boolean unusedKeys() {
      return argument
          .keys()
          .map(keys -> keys.stream().anyMatch(key -> !format.named().contains(key)))
          .orElse(false);
    }

    private boolean missingKeys() {
      return hasNamed()
          && argument.keys().map(keys -> !keys.containsAll(format.named())).orElse(false);
    }
  }
}
