package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.Layout.node;
import static dev.ward.analysis.domain.rule.check.Layout.startOf;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Map;

/** Builds the call, creation and statement trees and the facts that the type-aware checks read. */
final class TypedTrees {

  static final String DEBUG = "debug";
  static final String COMPILATION_UNIT = "compilationUnit";
  private static final String EXPRESSION = "expression";
  private static final String IDENTIFIER = "identifier";
  private static final String TOKEN_IDENTIFIER = "IDENTIFIER";

  SpanFacts fact(final Object... parts) {
    CallTarget call = null;
    TypeShape shape = null;
    for (final Object part : parts) {
      if (part instanceof CallTarget found) {
        call = found;
      } else if (part instanceof TypeShape found) {
        shape = found;
      }
    }
    return new SpanFacts(null, shape, call, List.of(), List.of(), List.of(), List.of(), null, null);
  }

  CallTarget callOf(final String declaring, final String name) {
    return new CallTarget(
        TypeRef.of(declaring),
        name,
        List.of(),
        TypeRef.of("void"),
        false,
        false,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);
  }

  TypeShape shape(final TypeShape.Kind kind) {
    return new TypeShape(kind, false, false, false, List.of());
  }

  /** {@code log.<name>("x"[ + name])}, laid out from the layout's current position. */
  SyntaxNode logCall(final Layout layout, final String name, final boolean concatenate) {
    final SyntaxNode receiver = node(EXPRESSION, layout.token(TOKEN_IDENTIFIER, "log"));
    final SyntaxNode dot = layout.token("'.'", ".");
    final SyntaxNode methodName = layout.token(TOKEN_IDENTIFIER, name);
    final SyntaxNode open = layout.token("'('", "(");
    final SyntaxNode message = node(EXPRESSION, layout.token(TOKEN_IDENTIFIER, "\"x\""));
    final List<SyntaxNode> parts = new java.util.ArrayList<>(List.of(open, message));
    if (concatenate) {
      parts.add(layout.token("'+'", "+"));
      parts.add(node(EXPRESSION, layout.token(TOKEN_IDENTIFIER, "name")));
    }
    parts.add(layout.token("')'", ")"));
    final SyntaxNode arguments = node("arguments", parts.toArray(SyntaxNode[]::new));
    return node(
        EXPRESSION, receiver, dot, node("methodCall", node(IDENTIFIER, methodName), arguments));
  }

  /** {@code color.equals(RED)}. */
  SyntaxNode equalsCall(final Layout layout) {
    final SyntaxNode receiver = node(EXPRESSION, layout.token(TOKEN_IDENTIFIER, "color"));
    final SyntaxNode dot = layout.token("'.'", ".");
    final SyntaxNode methodName = layout.token(TOKEN_IDENTIFIER, "equals");
    final SyntaxNode open = layout.token("'('", "(");
    final SyntaxNode argument = node(EXPRESSION, layout.token(TOKEN_IDENTIFIER, "RED"));
    final SyntaxNode close = layout.token("')'", ")");
    return node(
        EXPRESSION,
        receiver,
        dot,
        node("methodCall", node(IDENTIFIER, methodName), node("arguments", open, argument, close)));
  }

  /** {@code new <name><<key>>}, whose key type is the fourth child's identifier. */
  SyntaxNode creator(final Layout layout, final String name, final String key) {
    final SyntaxNode created = node(IDENTIFIER, layout.token(TOKEN_IDENTIFIER, name));
    final SyntaxNode open = layout.token("'<'", "<");
    final SyntaxNode keyType = node("typeType", layout.token(TOKEN_IDENTIFIER, key));
    final SyntaxNode close = layout.token("'>'", ">");
    return node("creator", created, open, keyType, close);
  }

  /** {@code rows.next()} as an expression. */
  SyntaxNode nextExpression(final Layout layout) {
    final SyntaxNode receiver = node(EXPRESSION, layout.token(TOKEN_IDENTIFIER, "rows"));
    final SyntaxNode dot = layout.token("'.'", ".");
    final SyntaxNode methodName = layout.token(TOKEN_IDENTIFIER, "next");
    final SyntaxNode open = layout.token("'('", "(");
    final SyntaxNode close = layout.token("')'", ")");
    return node(
        EXPRESSION,
        receiver,
        dot,
        node("methodCall", node(IDENTIFIER, methodName), node("arguments", open, close)));
  }

  /** Facts that name {@code node}'s start. */
  UnitTypeFacts at(final SyntaxNode node, final Object... parts) {
    return new UnitTypeFacts(Map.of(startOf(node), fact(parts)));
  }
}
