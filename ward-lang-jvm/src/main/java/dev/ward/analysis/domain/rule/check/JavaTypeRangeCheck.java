package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a comparison of a {@code byte}, {@code short} or {@code char} with an integer literal
 * that the range of the type decides, as SpotBugs' UC_USELESS_CONDITION_TYPE does. An operand whose
 * type is unknown is never reported.
 */
public final class JavaTypeRangeCheck implements RuleCheck {

  private static final Pattern LITERAL = Pattern.compile("^-?\\d{1,9}$");
  private static final int PARTS = 3;
  private static final Map<String, long[]> RANGES =
      Map.of(
          "byte", new long[] {Byte.MIN_VALUE, Byte.MAX_VALUE},
          "short", new long[] {Short.MIN_VALUE, Short.MAX_VALUE},
          "char", new long[] {Character.MIN_VALUE, Character.MAX_VALUE});
  private static final Map<String, String> MIRROR =
      Map.of(
          "'<'", "'>'", "'>'", "'<'", "'<='", "'>='", "'>='", "'<='", "'=='", "'=='", "'!='",
          "'!='");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the type
   */
  public JavaTypeRangeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.CALLS_AND_TYPES;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      final SyntaxNode comparison = visit.node();
      decided(comparison, types)
          .ifPresent(
              type ->
                  matches.add(
                      CheckSupport.at(comparison.span(), CheckSupport.fill(message, type, ""))));
    }
    return List.copyOf(matches);
  }

  private static Optional<String> decided(final SyntaxNode comparison, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = comparison.children();
    if (parts.size() != PARTS || !MIRROR.containsKey(parts.get(1).kind())) {
      return Optional.empty();
    }
    final Optional<String> left = ranged(parts.get(0), parts.get(2), parts.get(1).kind(), types);
    return left.isPresent()
        ? left
        : ranged(parts.get(2), parts.get(0), MIRROR.get(parts.get(1).kind()), types);
  }

  private static Optional<String> ranged(
      final SyntaxNode variable,
      final SyntaxNode literal,
      final String operator,
      final UnitTypeFacts types) {
    final String text = SyntaxWalk.text(literal);
    final Optional<TypeRef> type = types.typeOf(variable.span());
    if (!LITERAL.matcher(text).matches()
        || type.isEmpty()
        || !type.get().primitive()
        || type.get().arrayDepth() != 0
        || !RANGES.containsKey(type.get().name())) {
      return Optional.empty();
    }
    final long[] range = RANGES.get(type.get().name());
    return constant(range[0], range[1], operator, Long.parseLong(text))
        ? Optional.of(type.get().name())
        : Optional.empty();
  }

  private static boolean constant(
      final long low, final long high, final String operator, final long value) {
    return switch (operator) {
      case "'<'" -> high < value || low >= value;
      case "'<='" -> high <= value || low > value;
      case "'>'" -> low > value || high <= value;
      case "'>='" -> low >= value || high < value;
      default -> value < low || value > high;
    };
  }
}
