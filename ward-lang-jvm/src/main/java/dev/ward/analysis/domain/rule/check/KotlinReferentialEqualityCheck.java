package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a Kotlin {@code ===} or {@code !==} where either operand has one of the forbidden types.
 * The forbidden names are JVM names, and {@code kotlin.String} means {@code java.lang.String}. An
 * operand whose type is unknown is never reported.
 */
public final class KotlinReferentialEqualityCheck implements RuleCheck {
  private static final String COMPARISON = "comparison";
  private static final String EQUALITY = "equalityComparison";
  private static final String OPERATION = "equalityOperation";
  private static final String IDENTITY = "===";
  private static final String NEGATED_IDENTITY = "!==";
  private static final String KOTLIN_STRING = "kotlin.String";
  private static final String JAVA_STRING = "java.lang.String";
  private static final int OPERAND_COUNT = 3;
  private static final int LEFT = 0;
  private static final int OPERATOR = 1;
  private static final int RIGHT = 2;
  private static final FactNeed NEED = new FactNeed(Set.of(COMPARISON), Set.of(FactKind.TYPE));

  private final Set<String> forbidden;
  private final String message;

  /**
   * Creates the check.
   *
   * @param forbidden the type names whose operands are reported, such as {@code kotlin.String}
   * @param message the message reported at the comparison
   */
  public KotlinReferentialEqualityCheck(final Set<String> forbidden, final String message) {
    this.forbidden =
        Set.copyOf(forbidden.stream().map(KotlinReferentialEqualityCheck::jvmName).toList());
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
    return SyntaxWalk.visits(root, Set.of(EQUALITY)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(equality -> isForbidden(equality, types))
        .map(equality -> CheckSupport.at(equality.span(), message))
        .toList();
  }

  private boolean isForbidden(final SyntaxNode equality, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = equality.children();
    return parts.size() == OPERAND_COUNT
        && OPERATION.equals(parts.get(OPERATOR).kind())
        && isReferential(SyntaxWalk.text(parts.get(OPERATOR)))
        && (isForbiddenOperand(parts.get(LEFT), types)
            || isForbiddenOperand(parts.get(RIGHT), types));
  }

  private static boolean isReferential(final String operator) {
    return IDENTITY.equals(operator) || NEGATED_IDENTITY.equals(operator);
  }

  private boolean isForbiddenOperand(final SyntaxNode operand, final UnitTypeFacts types) {
    return COMPARISON.equals(operand.kind())
        && types
            .typeOf(operand.span())
            .filter(type -> type.arrayDepth() == 0 && forbidden.contains(type.name()))
            .isPresent();
  }

  private static String jvmName(final String name) {
    return KOTLIN_STRING.equals(name) ? JAVA_STRING : name;
  }
}
