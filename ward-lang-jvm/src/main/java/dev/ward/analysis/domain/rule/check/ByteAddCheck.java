package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports {@code (x << 8) + b} where {@code b} is a byte: the byte is sign-extended, so a negative
 * byte also changes the high bits that the shift cleared. The left operand must shift left by at
 * least a byte. It reports nothing when the type of the byte operand is unknown.
 */
public final class ByteAddCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final int PARTS = 3;
  private static final int BYTE_BITS = 8;
  private static final Pattern SHIFTED = Pattern.compile("<<(\\d+)\\)?$");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the addition
   */
  public ByteAddCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(sum -> addsByte(sum, types))
        .map(sum -> CheckSupport.at(sum.span(), message))
        .toList();
  }

  private static boolean addsByte(final SyntaxNode sum, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = sum.children();
    if (parts.size() != PARTS || !"+".equals(SyntaxWalk.text(parts.get(1)))) {
      return false;
    }
    return shiftedByAByte(parts.get(0)) && isByte(parts.get(2), types)
        || shiftedByAByte(parts.get(2)) && isByte(parts.get(0), types);
  }

  private static boolean shiftedByAByte(final SyntaxNode operand) {
    final Matcher shift = SHIFTED.matcher(SyntaxWalk.text(operand));
    return shift.find() && Integer.parseInt(shift.group(1)) >= BYTE_BITS;
  }

  private static boolean isByte(final SyntaxNode operand, final UnitTypeFacts types) {
    return types
        .typeOf(operand.span())
        .filter(TypeRef::primitive)
        .filter(type -> "byte".equals(type.name()) && type.arrayDepth() == 0)
        .isPresent();
  }
}
