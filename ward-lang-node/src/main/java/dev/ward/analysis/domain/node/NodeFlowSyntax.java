package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the TypeScript and JavaScript parse trees for control-flow lowering. Both grammars share
 * the rule names used here; they differ only in where a function body is wrapped, which {@link
 * #body(SyntaxNode)} reads. Comment and error nodes are skipped.
 */
final class NodeFlowSyntax {

  private static final String COMMENT = "comment";
  private static final String ERROR = "error";
  private static final String TEXT = "text";
  private static final String SOURCE_ELEMENTS = "sourceElements";
  private static final String STATEMENT_LIST = "statementList";
  private static final String IDENTIFIER_TOKEN = "Identifier";
  private static final String EXPRESSION_SEQUENCE = "expressionSequence";
  private static final Set<String> FUNCTIONS =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "propertyMemberDeclaration");

  private NodeFlowSyntax() {}

  /** Returns the children that are grammar parts, in source order. */
  static List<SyntaxNode> parts(final SyntaxNode node) {
    final List<SyntaxNode> parts = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (!COMMENT.equals(child.kind()) && !ERROR.equals(child.kind())) {
        parts.add(child);
      }
    }
    return parts;
  }

  /** Returns the first of the given parts that has the given kind, or null. */
  static SyntaxNode firstOf(final List<SyntaxNode> parts, final String kind) {
    for (final SyntaxNode part : parts) {
      if (kind.equals(part.kind())) {
        return part;
      }
    }
    return null;
  }

  /** Returns the given parts that have the given kind, in source order. */
  static List<SyntaxNode> allOf(final List<SyntaxNode> parts, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode part : parts) {
      if (kind.equals(part.kind())) {
        found.add(part);
      }
    }
    return found;
  }

  /** Returns the source text of a node, the concatenation of its tokens without comments. */
  static String text(final SyntaxNode node) {
    if (COMMENT.equals(node.kind())) {
      return "";
    }
    if (node.children().isEmpty()) {
      return node.fields().getOrDefault(TEXT, "");
    }
    final StringBuilder out = new StringBuilder();
    for (final SyntaxNode child : node.children()) {
      out.append(text(child));
    }
    return out.toString();
  }

  /** Returns the tokens of a node joined by single spaces, so that the words of it stay apart. */
  static String spaced(final SyntaxNode node) {
    final List<String> tokens = new ArrayList<>();
    collectTokens(node, tokens);
    return String.join(" ", tokens);
  }

  private static void collectTokens(final SyntaxNode node, final List<String> tokens) {
    if (COMMENT.equals(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      final String token = node.fields().getOrDefault(TEXT, "");
      if (!token.isEmpty()) {
        tokens.add(token);
      }
    } else {
      node.children().forEach(child -> collectTokens(child, tokens));
    }
  }

  /** Returns the first identifier token under a node, or null. */
  static SyntaxNode firstIdentifier(final SyntaxNode node) {
    if (IDENTIFIER_TOKEN.equals(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode found = firstIdentifier(child);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /** Strips the expression sequences and parentheses around an expression. */
  static SyntaxNode unwrap(final SyntaxNode expression) {
    SyntaxNode current = expression;
    boolean stripped = true;
    while (stripped) {
      stripped = false;
      final List<SyntaxNode> parts = parts(current);
      if (EXPRESSION_SEQUENCE.equals(current.kind()) && parts.size() == 1) {
        current = parts.get(0);
        stripped = true;
      } else if (NodeExpressions.isParenthesized(current)) {
        current = parts.get(1);
        stripped = true;
      }
    }
    return current;
  }

  /** Reports whether a node is a function with a body, which is lowered as its own graph. */
  static boolean isFunction(final SyntaxNode node) {
    return FUNCTIONS.contains(node.kind()) && body(node) != null;
  }

  /**
   * Returns the body of a function: the {@code functionBody} or the {@code arrowFunctionBody}, or
   * null when the function has none, as with an overload signature.
   */
  static SyntaxNode body(final SyntaxNode function) {
    final List<SyntaxNode> parts = parts(function);
    final SyntaxNode body = firstOf(parts, "functionBody");
    return body != null ? body : firstOf(parts, "arrowFunctionBody");
  }

  /** Returns the statements of a body, or of a block, in source order. */
  static List<SyntaxNode> statements(final SyntaxNode container) {
    final List<SyntaxNode> statements = new ArrayList<>();
    for (final SyntaxNode part : parts(container)) {
      if (SOURCE_ELEMENTS.equals(part.kind())) {
        for (final SyntaxNode element : allOf(parts(part), "sourceElement")) {
          statements.add(firstOf(parts(element), "statement"));
        }
      } else if (STATEMENT_LIST.equals(part.kind())) {
        statements.addAll(allOf(parts(part), "statement"));
      }
    }
    return statements;
  }
}
