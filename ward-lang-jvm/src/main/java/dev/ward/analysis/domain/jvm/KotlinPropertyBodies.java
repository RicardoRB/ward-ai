package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The bodies that a property declares: its initializer or delegate, its getter and its setter. Each
 * is lowered as a body of its own, named after the property, so a source or a sink in a property is
 * analysed like one in a function.
 */
final class KotlinPropertyBodies {

  private static final String EQUALS = "=";
  private static final String DELEGATE = "by";
  private static final String BLOCK = "block";
  private static final String EXPRESSION = "expression";
  private static final String BODY = "functionBody";
  private static final String GETTER = "getter";
  private static final String SETTER = "setter";

  private KotlinPropertyBodies() {}

  /**
   * One body of a property.
   *
   * @param name the name of the body, such as {@code <prop:name>}
   * @param owner the node whose span the body covers
   * @param body a block, or a function body that holds an expression
   */
  record Body(String name, SyntaxNode owner, SyntaxNode body) {}

  /**
   * Finds the bodies of a property.
   *
   * @param property a {@code propertyDeclaration}
   * @return its initializer, getter and setter bodies, in source order
   */
  static List<Body> of(final SyntaxNode property) {
    final SyntaxNode variable = KotlinFlowSyntax.child(property, "variableDeclaration");
    final String name = variable == null ? "<destructured>" : name(variable);
    final List<Body> found = new ArrayList<>();
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(property);
    for (int index = 0; index < parts.size(); index++) {
      final SyntaxNode part = parts.get(index);
      if (isInitializer(parts, index)) {
        found.add(new Body("<prop:" + name + ">", property, valueBody(part, parts.get(index + 1))));
      } else if (GETTER.equals(part.kind())) {
        accessor(part, "<get:" + name + ">", found);
      } else if (SETTER.equals(part.kind())) {
        final SyntaxNode body = KotlinFlowSyntax.child(part, BODY);
        if (body != null) {
          found.add(new Body("<set:" + name + ">", part, bodyOf(body)));
        }
      }
    }
    return found;
  }

  private static String name(final SyntaxNode variable) {
    return KotlinFlowSyntax.text(KotlinScopeWalk.simple(variable));
  }

  /** The token {@code =} or {@code by} that is followed by an expression. */
  private static boolean isInitializer(final List<SyntaxNode> parts, final int index) {
    final String token = KotlinFlowSyntax.tokenText(parts.get(index));
    return (EQUALS.equals(token) || DELEGATE.equals(token))
        && index + 1 < parts.size()
        && EXPRESSION.equals(parts.get(index + 1).kind());
  }

  private static void accessor(final SyntaxNode getter, final String name, final List<Body> found) {
    final SyntaxNode block = KotlinFlowSyntax.child(getter, BLOCK);
    if (block != null) {
      found.add(new Body(name, getter, block));
      return;
    }
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(getter);
    for (int index = 0; index < parts.size() - 1; index++) {
      if (isInitializer(parts, index)) {
        found.add(new Body(name, getter, valueBody(parts.get(index), parts.get(index + 1))));
      }
    }
  }

  /** The body of a function, a setter or a getter: its block when it has one, else itself. */
  private static SyntaxNode bodyOf(final SyntaxNode body) {
    final SyntaxNode block = KotlinFlowSyntax.child(body, BLOCK);
    return block == null ? body : block;
  }

  /** A body that holds the expression after the token, shaped like {@code = expression}. */
  private static SyntaxNode valueBody(final SyntaxNode token, final SyntaxNode value) {
    return new SyntaxNode(
        BODY,
        KotlinFlowSyntax.between(token.span(), value.span()),
        Map.of(),
        List.of(token, value));
  }
}
