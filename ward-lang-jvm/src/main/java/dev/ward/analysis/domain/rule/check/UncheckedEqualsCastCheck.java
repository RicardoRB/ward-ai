package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports the first cast of the parameter of {@code equals(Object)} when no {@code instanceof} on
 * that parameter and no {@code getClass()} call comes before it: {@code equals} must return false
 * for an object of another class instead of throwing {@code ClassCastException}.
 */
public final class UncheckedEqualsCastCheck implements UnitCheck {
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String EXPRESSION = "expression";
  private static final String TYPE_TYPE = "typeType";
  private static final String INSTANCEOF = "'instanceof'";
  private static final String PARAMETER_ID = "variableDeclaratorId";
  private static final String EQUALS = "equals";
  private static final String OBJECT = "Object";
  private static final String GET_CLASS = "getClass";
  private static final int CAST_PARTS = 4;
  private static final int CAST_TYPE = 1;
  private static final int CAST_OPERAND = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the cast
   */
  public UncheckedEqualsCastCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION)).stream()
        .flatMap(visit -> DeclaredMethods.of(visit.node()).stream())
        .filter(
            method ->
                EQUALS.equals(method.name()) && method.parameterTypes().equals(List.of(OBJECT)))
        .flatMap(method -> unguardedCast(method.node()).stream())
        .map(cast -> CheckSupport.at(cast.span(), message))
        .toList();
  }

  private static Optional<SyntaxNode> unguardedCast(final SyntaxNode method) {
    final Optional<String> parameter =
        SyntaxWalk.descendants(method).stream()
            .filter(node -> PARAMETER_ID.equals(node.kind()))
            .findFirst()
            .map(SyntaxWalk::text);
    if (parameter.isEmpty()) {
      return Optional.empty();
    }
    final List<SyntaxNode> nodes = SyntaxWalk.descendants(method);
    final Optional<SyntaxNode> cast =
        nodes.stream().filter(node -> castsParameter(node, parameter.get())).findFirst();
    return cast.filter(
        found ->
            nodes.stream()
                .noneMatch(
                    node ->
                        guards(node, parameter.get())
                            && node.span().startOffset() < found.span().startOffset()));
  }

  private static boolean castsParameter(final SyntaxNode node, final String parameter) {
    final List<SyntaxNode> parts = node.children();
    return EXPRESSION.equals(node.kind())
        && parts.size() == CAST_PARTS
        && TYPE_TYPE.equals(parts.get(CAST_TYPE).kind())
        && !OBJECT.equals(SyntaxWalk.text(parts.get(CAST_TYPE)))
        && parameter.equals(SyntaxWalk.text(parts.get(CAST_OPERAND)));
  }

  private static boolean guards(final SyntaxNode node, final String parameter) {
    final List<SyntaxNode> parts = node.children();
    if (EXPRESSION.equals(node.kind())
        && parts.size() > 1
        && INSTANCEOF.equals(parts.get(1).kind())) {
      return parameter.equals(SyntaxWalk.text(parts.get(0)));
    }
    return "methodCall".equals(node.kind())
        && parts.stream()
            .anyMatch(
                part ->
                    "identifier".equals(part.kind()) && GET_CLASS.equals(SyntaxWalk.text(part)));
  }
}
