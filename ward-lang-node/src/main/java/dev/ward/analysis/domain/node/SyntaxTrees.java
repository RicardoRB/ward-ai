package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the functions and declarations of a shared syntax tree for the structural rules. */
final class SyntaxTrees {

  private static final String TEXT = "text";
  private static final String END_OF_FILE = "<EOF>";
  private static final String IDENTIFIER_TOKEN = "Identifier";

  /** Nodes that start a function. A function also needs one of {@link #HEADER} or {@link #BODY}. */
  private static final Set<String> FUNCTION =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "propertyMemberDeclaration");

  /** Children that hold a function's parameter list. */
  private static final Set<String> HEADER =
      Set.of("callSignature", "formalParameterList", "arrowFunctionParameters");

  /** Children that hold a function's body; their statements belong to the function. */
  private static final Set<String> BODY = Set.of("functionBody", "arrowFunctionBody");

  /** Nodes that hold one formal parameter. */
  private static final Set<String> PARAMETER = Set.of("parameter", "formalParameterArg");

  private SyntaxTrees() {}

  /** Returns every function in the tree, outer functions before the functions they contain. */
  static List<SyntaxNode> functions(final SyntaxNode root) {
    final List<SyntaxNode> found = new ArrayList<>();
    collectFunctions(root, found);
    return found;
  }

  /**
   * Reports whether {@code node} is a function: a function kind with a parameter list or a body.
   * The grammars omit an empty parameter list, so a body is also a sign of a function.
   */
  static boolean isFunction(final SyntaxNode node) {
    return FUNCTION.contains(node.kind()) && (hasChildOf(node, HEADER) || hasChildOf(node, BODY));
  }

  /**
   * Returns the line of the last real token in the tree. The end-of-file token sits after the final
   * newline, so the span of the root does not stop at the last line of code.
   */
  static int lastLine(final SyntaxNode node) {
    int line = 0;
    if (node.fields().containsKey(TEXT) && !END_OF_FILE.equals(node.fields().get(TEXT))) {
      line = node.span().endLine();
    }
    for (final SyntaxNode child : node.children()) {
      line = Math.max(line, lastLine(child));
    }
    return line;
  }

  /** Counts the formal parameters of a function, not counting those inside its body. */
  static int parameterCount(final SyntaxNode function) {
    int count = 0;
    for (final SyntaxNode child : function.children()) {
      count += countParameters(child);
    }
    return count;
  }

  /** Returns the first identifier token in source order under {@code node}, or null. */
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

  /** Returns the number of lines a span covers, both ends included. */
  static int lines(final Span span) {
    return span.endLine() - span.startLine() + 1;
  }

  private static void collectFunctions(final SyntaxNode node, final List<SyntaxNode> found) {
    if (isFunction(node)) {
      found.add(node);
    }
    for (final SyntaxNode child : node.children()) {
      collectFunctions(child, found);
    }
  }

  private static boolean hasChildOf(final SyntaxNode node, final Set<String> kinds) {
    for (final SyntaxNode child : node.children()) {
      if (kinds.contains(child.kind())) {
        return true;
      }
    }
    return false;
  }

  private static int countParameters(final SyntaxNode node) {
    if (PARAMETER.contains(node.kind())) {
      return 1;
    }
    if (BODY.contains(node.kind()) || isFunction(node)) {
      return 0;
    }
    int count = 0;
    for (final SyntaxNode child : node.children()) {
      count += countParameters(child);
    }
    return count;
  }
}
