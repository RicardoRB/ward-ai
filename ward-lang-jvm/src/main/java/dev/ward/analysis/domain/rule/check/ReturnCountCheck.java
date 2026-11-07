package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method with more return statements than its limit: {@code max} for a method that
 * returns a value, {@code max-for-void} for a void method. Returns inside lambdas and nested
 * classes belong to those bodies and are not counted.
 */
public final class ReturnCountCheck implements UnitCheck {

  private static final String RETURN = "'return'";
  private static final String VOID = "void";
  private static final String TYPE_OR_VOID = "typeTypeOrVoid";
  private static final Set<String> OWN_BODY_BREAKS =
      Set.of("lambdaExpression", "classBody", "interfaceBody");

  private final int max;
  private final int maxForVoid;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest number of returns in a method that returns a value
   * @param maxForVoid the largest number of returns in a void method
   * @param message the message; {@code {value}} is the count and {@code {max}} the limit
   * @throws IllegalArgumentException if a limit is negative
   */
  public ReturnCountCheck(final int max, final int maxForVoid, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.maxForVoid = CheckSupport.nonNegative(maxForVoid, "max-for-void");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("methodDeclaration"))) {
      final SyntaxNode method = visit.node();
      final int count = returns(method);
      final int limit = isVoid(method) ? maxForVoid : max;
      if (count > limit) {
        matches.add(
            CheckSupport.at(
                method.span(),
                CheckSupport.fill(message, String.valueOf(count), String.valueOf(limit))));
      }
    }
    return List.copyOf(matches);
  }

  private static int returns(final SyntaxNode method) {
    int count = 0;
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(method, Set.of("statement"))) {
      if (isReturn(visit.node()) && ownBody(visit.ancestors())) {
        count++;
      }
    }
    return count;
  }

  private static boolean isReturn(final SyntaxNode statement) {
    return statement.children().stream().anyMatch(c -> RETURN.equals(c.kind()));
  }

  private static boolean ownBody(final List<SyntaxNode> ancestors) {
    return ancestors.stream().noneMatch(a -> OWN_BODY_BREAKS.contains(a.kind()));
  }

  private static boolean isVoid(final SyntaxNode method) {
    return method.children().stream()
        .anyMatch(c -> TYPE_OR_VOID.equals(c.kind()) && VOID.equals(SyntaxWalk.text(c)));
  }
}
