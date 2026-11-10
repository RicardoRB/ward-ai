package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an explicit type argument on a call of a generic method with one type parameter when an
 * argument of the call already has that type and is passed for a parameter of exactly the type
 * variable, as Error Prone's UnnecessaryTypeArgument does: inference gives the same type. A call
 * whose target or argument types are unknown is never reported.
 */
public final class UnnecessaryTypeArgumentCheck extends FactsCheck {

  private static final Pattern EXPLICIT = Pattern.compile("^[\\w.]*\\.<([\\w.]+)>\\w+\\(");
  private static final String EXPRESSION = "expression";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final String INVOCATION = "explicitGenericInvocation";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call; {@code {value}} is the type argument
   */
  public UnnecessaryTypeArgumentCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(call -> reportOf(call, types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode call, final UnitTypeFacts types) {
    final Matcher explicit = EXPLICIT.matcher(SyntaxWalk.text(call));
    final CallTarget target = types.callTarget(call.span()).orElse(null);
    if (!explicit.find() || target == null || target.typeParameters().size() != 1) {
      return List.of();
    }
    final String variable = target.typeParameters().get(0);
    final List<SyntaxNode> arguments = explicitArguments(call);
    final boolean redundant =
        arguments.size() == target.parameters().size()
            && inferred(target, variable, arguments, explicit.group(1), types);
    return redundant
        ? List.of(CheckSupport.at(call.span(), CheckSupport.fill(message, explicit.group(1), "")))
        : List.of();
  }

  private static List<SyntaxNode> explicitArguments(final SyntaxNode call) {
    return SyntaxWalk.descendants(call).stream()
        .filter(node -> INVOCATION.equals(node.kind()))
        .findFirst()
        .flatMap(
            invocation ->
                SyntaxWalk.descendants(invocation).stream()
                    .filter(node -> EXPRESSION_LIST.equals(node.kind()))
                    .findFirst())
        .map(SyntaxNode::children)
        .orElse(List.of())
        .stream()
        .filter(node -> EXPRESSION.equals(node.kind()))
        .toList();
  }

  private static boolean inferred(
      final CallTarget target,
      final String variable,
      final List<SyntaxNode> arguments,
      final String written,
      final UnitTypeFacts types) {
    for (int index = 0; index < arguments.size(); index++) {
      final TypeRef parameter = target.parameters().get(index);
      if (variable.equals(parameter.name()) && parameter.arrayDepth() == 0) {
        return types
            .typeOf(arguments.get(index).span())
            .filter(given -> given.arrayDepth() == 0 && !given.primitive())
            .filter(given -> given.name().equals(written) || given.name().endsWith("." + written))
            .isPresent();
      }
    }
    return false;
  }
}
