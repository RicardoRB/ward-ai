package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code return} with a value inside a class constructor, like ESLint {@code
 * no-constructor-return}. A nested function or class is another body, so its returns are not
 * reported. The JavaScript grammar names a constructor as a method; the TypeScript grammar names it
 * as a constructor declaration.
 */
public final class NodeConstructorReturnCheck implements RuleCheck {

  private static final String RETURN = "returnStatement";
  private static final String VALUE = "expressionSequence";
  private static final String METHOD = "methodDefinition";
  private static final String CONSTRUCTOR_DECLARATION = "constructorDeclaration";
  private static final String NAME = "classElementName";
  private static final String BODY = "functionBody";
  private static final String CONSTRUCTOR = "constructor";
  private static final Set<String> NESTED =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "constructorDeclaration",
          "classDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeConstructorReturnCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (isConstructor(node)) {
      node.children().stream()
          .filter(child -> BODY.equals(child.kind()))
          .forEach(body -> returns(body, matches));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  private void returns(final SyntaxNode node, final List<RuleMatch> matches) {
    for (final SyntaxNode child : node.children()) {
      if (RETURN.equals(child.kind()) && hasValue(child)) {
        matches.add(new RuleMatch(child.span(), message));
      } else if (!NESTED.contains(child.kind())) {
        returns(child, matches);
      }
    }
  }

  private static boolean hasValue(final SyntaxNode ret) {
    return ret.children().stream().anyMatch(child -> VALUE.equals(child.kind()));
  }

  private static boolean isConstructor(final SyntaxNode node) {
    return CONSTRUCTOR_DECLARATION.equals(node.kind())
        || METHOD.equals(node.kind())
            && node.children().stream()
                .filter(child -> NAME.equals(child.kind()))
                .anyMatch(name -> CONSTRUCTOR.equals(LeafText.of(name)));
  }
}
