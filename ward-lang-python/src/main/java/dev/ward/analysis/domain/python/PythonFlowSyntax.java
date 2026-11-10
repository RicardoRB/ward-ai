package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** The syntax predicates that the Python control-flow lowering and its flow rules share. */
public final class PythonFlowSyntax {

  private static final String CALL = "(";
  private static final String AWAIT = "await";
  private static final String TRUE = "True";
  private static final String ONE = "1";
  private static final String PATTERNS = "patterns";
  private static final String GUARD = "guard";
  private static final Set<String> NON_IDENTIFIERS = Set.of("None", TRUE, "False");
  private static final int SINGLE_TOKEN = 1;

  private PythonFlowSyntax() {}

  /** The direct children of {@code node} whose kind is {@code kind}, in source order. */
  public static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return PythonSyntax.children(node, kind);
  }

  /** The first direct child of {@code node} whose kind is {@code kind}, or null when none is. */
  public static SyntaxNode child(final SyntaxNode node, final String kind) {
    return children(node, kind).stream().findFirst().orElse(null);
  }

  /** Whether {@code node} has a direct child token with {@code text}. */
  public static boolean hasToken(final SyntaxNode node, final String text) {
    return node.children().stream().anyMatch(child -> PythonSyntax.isToken(child, text));
  }

  /** A call or an await may raise, so an enclosing try can catch it. */
  public static boolean mayThrow(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      final String text = PythonSyntax.text(node);
      return CALL.equals(text) || AWAIT.equals(text);
    }
    return node.children().stream().anyMatch(PythonFlowSyntax::mayThrow);
  }

  /** A condition that is always true, so a loop never leaves through its condition. */
  public static boolean isAlwaysTrue(final SyntaxNode condition) {
    final String text = onlyText(condition);
    return TRUE.equals(text) || ONE.equals(text);
  }

  /** A case that always matches: a single identifier pattern with no guard. */
  public static boolean isCatchAll(final SyntaxNode caseBlock) {
    if (!children(caseBlock, GUARD).isEmpty()) {
      return false;
    }
    final List<SyntaxNode> tokens = PythonSyntax.tokens(child(caseBlock, PATTERNS));
    if (tokens.size() != SINGLE_TOKEN) {
      return false;
    }
    final String text = PythonSyntax.text(tokens.get(0));
    return text.matches("[A-Za-z_]\\w*") && !NON_IDENTIFIERS.contains(text);
  }

  /** The text of {@code node} when it is a single token, or an empty string otherwise. */
  public static String onlyText(final SyntaxNode node) {
    final List<SyntaxNode> tokens = PythonSyntax.tokens(node);
    return tokens.size() == SINGLE_TOKEN ? PythonSyntax.text(tokens.get(0)) : "";
  }
}
