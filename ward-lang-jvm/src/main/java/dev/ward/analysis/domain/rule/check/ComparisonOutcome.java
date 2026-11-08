package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** What a comparison method returns: the sign of a constant, or something that is not constant. */
enum ComparisonOutcome {
  NEGATIVE,
  ZERO,
  POSITIVE,
  NONCONSTANT;

  private static final String STATEMENT = "statement";
  private static final Pattern INTEGER = Pattern.compile("-?\\d{1,9}");
  private static final int CONDITIONAL_PARTS = 5;
  private static final int TRUE_BRANCH = 2;
  private static final int FALSE_BRANCH = 4;
  private static final int RETURN_WITH_VALUE = 2;

  static Set<ComparisonOutcome> returned(final SyntaxNode body, final UnitTypeFacts types) {
    final Set<ComparisonOutcome> seen = EnumSet.noneOf(ComparisonOutcome.class);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(body, Set.of(STATEMENT))) {
      final List<SyntaxNode> parts = visit.node().children();
      if ("'return'".equals(parts.get(0).kind()) && parts.size() >= RETURN_WITH_VALUE) {
        collect(parts.get(1), types, seen);
      }
    }
    return seen;
  }

  private static void collect(
      final SyntaxNode expression, final UnitTypeFacts types, final Set<ComparisonOutcome> seen) {
    final List<SyntaxNode> parts = expression.children();
    if (parts.size() == CONDITIONAL_PARTS && "'?'".equals(parts.get(1).kind())) {
      collect(parts.get(TRUE_BRANCH), types, seen);
      collect(parts.get(FALSE_BRANCH), types, seen);
    } else {
      seen.add(classify(expression, types));
    }
  }

  static ComparisonOutcome classify(final SyntaxNode expression, final UnitTypeFacts types) {
    final String text = SyntaxWalk.text(expression);
    if (INTEGER.matcher(text).matches()) {
      return sign(Long.parseLong(text));
    }
    return types
        .constant(expression.span())
        .map(ConstantValue::value)
        .filter(Long.class::isInstance)
        .map(value -> sign((Long) value))
        .orElse(NONCONSTANT);
  }

  private static ComparisonOutcome sign(final long value) {
    if (value < 0) {
      return NEGATIVE;
    }
    return value > 0 ? POSITIVE : ZERO;
  }
}
