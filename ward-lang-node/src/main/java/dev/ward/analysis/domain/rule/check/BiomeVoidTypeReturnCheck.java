package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a return with a value in a function whose declared return type is exactly void, at the
 * return statement, as Biome 2.5.15's {@code noVoidTypeReturn} does. A bare return and a {@code
 * return void expression} are not reported.
 */
public final class BiomeVoidTypeReturnCheck implements RuleCheck {

  private static final String RETURN = "returnStatement";
  private static final String ARGUMENT = "expressionSequence";
  private static final String VOID = "void";
  private static final String VOID_TYPE = "void";
  private static final String TYPE = "type_";
  private static final String ANNOTATION = "typeAnnotation";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final String ARROW = "arrowFunctionDeclaration";
  private static final String METHOD = "generatorMethod";
  private static final String FUNCTION_EXPRESSION = "anonymousFunction";
  private static final String SINGLE_EXPRESSION = "singleExpression";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeVoidTypeReturnCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the ancestors' return types are read through the tree. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the void-return check needs the tree of its functions");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(RETURN)).stream()
        .filter(visit -> hasValue(visit.node()) && !voidArgument(visit.node()))
        .filter(visit -> enclosingReturnsVoid(visit.ancestors()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean hasValue(final SyntaxNode statement) {
    return statement.children().stream().anyMatch(child -> ARGUMENT.equals(child.kind()));
  }

  /** A return whose value starts with the void operator. */
  private static boolean voidArgument(final SyntaxNode statement) {
    return statement.children().stream()
        .filter(child -> ARGUMENT.equals(child.kind()))
        .flatMap(argument -> argument.children().stream())
        .filter(child -> SINGLE_EXPRESSION.equals(child.kind()))
        .anyMatch(expression -> hasToken(expression, VOID));
  }

  /** The nearest function among the ancestors decides it; a nested arrow stops the search. */
  private static boolean enclosingReturnsVoid(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode node = ancestors.get(index);
      if (isFunction(node)) {
        return annotationChild(node).filter(BiomeVoidTypeReturnCheck::isVoid).isPresent();
      }
    }
    return false;
  }

  private static boolean isFunction(final SyntaxNode node) {
    return ARROW.equals(node.kind())
        || FUNCTION_EXPRESSION.equals(node.kind())
        || METHOD.equals(node.kind())
        || node.children().stream().anyMatch(child -> CALL_SIGNATURE.equals(child.kind()));
  }

  /** The return annotation: the function's own, or the one of its call signature. */
  private static Optional<SyntaxNode> annotationChild(final SyntaxNode node) {
    final Optional<SyntaxNode> signature =
        node.children().stream().filter(child -> CALL_SIGNATURE.equals(child.kind())).findFirst();
    return signature.isPresent() ? directAnnotation(signature.get()) : directAnnotation(node);
  }

  private static Optional<SyntaxNode> directAnnotation(final SyntaxNode node) {
    return node.children().stream().filter(child -> ANNOTATION.equals(child.kind())).findFirst();
  }

  private static boolean isVoid(final SyntaxNode annotation) {
    return annotation.children().stream()
        .filter(child -> TYPE.equals(child.kind()))
        .anyMatch(type -> VOID_TYPE.equals(SyntaxWalk.text(type)));
  }

  /** Whether the node has a token child with this text, such as the = of a declaration. */
  private static boolean hasToken(final SyntaxNode node, final String text) {
    return node.children().stream()
        .anyMatch(child -> child.children().isEmpty() && text.equals(child.fields().get("text")));
  }
}
