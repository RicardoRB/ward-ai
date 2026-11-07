package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each parameter list with at least the minimum number of parameters. Parameters of a
 * private constructor are not counted, as PMD does.
 */
public final class ParameterCountCheck implements UnitCheck {

  private static final String PARAMETERS = "formalParameters";
  private static final String PARAMETER = "formalParameter";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String MEMBER = "classBodyDeclaration";

  private final int minimum;
  private final String message;

  /**
   * Creates the check.
   *
   * @param minimum the parameter count from which a list is reported
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public ParameterCountCheck(final int minimum, final String message) {
    this.minimum = CheckSupport.nonNegative(minimum, "minimum");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(PARAMETERS)).stream()
        .filter(visit -> !inPrivateConstructor(visit.ancestors()))
        .flatMap(visit -> reportOf(visit.node()).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode list) {
    final long count =
        SyntaxWalk.descendants(list).stream().filter(n -> PARAMETER.equals(n.kind())).count();
    if (count < minimum) {
      return List.of();
    }
    return List.of(
        CheckSupport.at(
            list.span(),
            CheckSupport.fill(message, String.valueOf(count), String.valueOf(minimum))));
  }

  private static boolean inPrivateConstructor(final List<SyntaxNode> ancestors) {
    final int constructor = lastIndexOf(ancestors, CONSTRUCTOR);
    if (constructor < 0) {
      return false;
    }
    for (int index = constructor - 1; index >= 0; index--) {
      if (MEMBER.equals(ancestors.get(index).kind())) {
        return MemberDeclarations.modifiers(ancestors.get(index))
            .contains(MemberDeclarations.PRIVATE);
      }
    }
    return false;
  }

  private static int lastIndexOf(final List<SyntaxNode> nodes, final String kind) {
    for (int index = nodes.size() - 1; index >= 0; index--) {
      if (kind.equals(nodes.get(index).kind())) {
        return index;
      }
    }
    return -1;
  }
}
