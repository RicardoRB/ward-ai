package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of a method annotated {@code @ForOverride} from outside the class that declares
 * it, as Error Prone's ForOverride does. A call made inside the declaring class, or one that starts
 * with {@code super.}, is allowed. A call whose target is unknown is never reported.
 */
public final class ForOverrideCheck extends FactsCheck {

  private static final String ANNOTATION = "com.google.errorprone.annotations.ForOverride";
  private static final String EXPRESSION = "expression";
  private static final String SUPER_CALL = "super.";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call; {@code {value}} is the method name
   */
  public ForOverrideCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .filter(visit -> SyntaxWalk.text(visit.node()).endsWith(")"))
        .filter(visit -> !SyntaxWalk.text(visit.node()).startsWith(SUPER_CALL))
        .flatMap(visit -> reportOf(visit, types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxWalk.Visit visit, final UnitTypeFacts types) {
    return types
        .callTarget(visit.node().span())
        .filter(target -> target.annotations().contains(ANNOTATION))
        .filter(target -> !declaredAround(target, visit.ancestors()))
        .map(
            target ->
                CheckSupport.at(visit.node().span(), CheckSupport.fill(message, target.name(), "")))
        .stream()
        .toList();
  }

  private static boolean declaredAround(final CallTarget target, final List<SyntaxNode> ancestors) {
    final String declaring = simpleName(target.declaringType().name());
    return ancestors.stream()
        .filter(node -> MemberDeclarations.CLASS.equals(node.kind()))
        .anyMatch(node -> declaring.equals(ClassMembers.nameOf(node)));
  }

  private static String simpleName(final String qualified) {
    return qualified.substring(
        Math.max(qualified.lastIndexOf('.'), qualified.lastIndexOf('$')) + 1);
  }
}
