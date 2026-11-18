package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a comparison of a value with {@code Integer.MIN_VALUE} or {@code Integer.MAX_VALUE} that
 * is always true or always false. When the value is a name declared {@code long}, {@code float} or
 * {@code double}, or a cast to one of them, the comparison is a real range check and is not
 * reported.
 */
public final class IntLimitComparisonCheck implements UnitCheck {
  private static final Pattern VACUOUS =
      Pattern.compile(
          "^(.+?)(?:>=Integer\\.MIN_VALUE|<Integer\\.MIN_VALUE|<=Integer\\.MAX_VALUE|"
              + ">Integer\\.MAX_VALUE)$"
      );
  private static final Pattern WIDE = Pattern.compile("^(?:long|Long|float|Float|double|Double)$");
  private static final Pattern WIDE_CAST = Pattern.compile("^\\((?:long|float|double)\\)");
  private static final Set<String> OPERATORS = Set.of("'<'", "'>'", "'<='", "'>='");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public IntLimitComparisonCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("expression")).stream()
        .filter(
            visit -> visit.node().children().stream().anyMatch(c -> OPERATORS.contains(c.kind()))
        )
        .filter(IntLimitComparisonCheck::isNarrow)
        .map(visit -> CheckSupport.at(visit.node().span(), CheckSupport.fill(message, "", "")))
        .toList();
  }

  private static boolean isNarrow(final SyntaxWalk.Visit visit) {
    final Matcher matcher = VACUOUS.matcher(SyntaxWalk.text(visit.node()));
    if (!matcher.find()) {
      return false;
    }
    final String operand = matcher.group(1);
    return !WIDE_CAST.matcher(operand).find()
        && !WIDE.matcher(DeclaredTypes.of(operand, visit.ancestors())).matches();
  }
}
