package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reports each javadoc comment that is not placed before a declaration, like Checkstyle's
 * InvalidJavadocPosition. A comment inside a function or block body is never a declaration's doc.
 * Elsewhere the comment maps to the next token in the source; it is valid only when that token
 * starts a declaration, which is compared by position so the tree shape around comments does not
 * matter.
 */
public final class NodeJavadocPositionCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final String JAVADOC = "/**";
  private static final Set<String> MODIFIERS =
      Set.of("export", "default", "declare", "abstract", "async", "static", "public", "private",
          "protected", "readonly");
  private static final Set<String> BODIES = Set.of("functionBody", "block", "statementList");
  private static final Set<String> DECLARATIONS =
      Set.of(
          "classDeclaration",
          "functionDeclaration",
          "interfaceDeclaration",
          "typeAliasDeclaration",
          "enumDeclaration",
          "exportStatement",
          "variableStatement",
          "constructorDeclaration",
          "methodDefinition",
          "propertyMemberDeclaration",
          "fieldDefinition");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeJavadocPositionCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> javadocs = new ArrayList<>();
    final List<SyntaxNode> tokens = new ArrayList<>();
    final Set<Integer> declarations = new TreeSet<>();
    collect(root, false, javadocs, tokens, declarations);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode javadoc : javadocs) {
      if (inBody(javadoc) || !startsDeclaration(javadoc, tokens, declarations)) {
        found.add(new RuleMatch(javadoc.span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Gathers the javadoc comments, the tokens in source order, and the start of each declaration. */
  private static void collect(
      final SyntaxNode node,
      final boolean inBody,
      final List<SyntaxNode> javadocs,
      final List<SyntaxNode> tokens,
      final Set<Integer> declarations) {
    if (isJavadoc(node)) {
      javadocs.add(inBody ? bodyMarker(node) : node);
      return;
    }
    if (DECLARATIONS.contains(node.kind())) {
      declarations.add(firstLeaf(node).span().startOffset());
    }
    if (node.children().isEmpty()) {
      if (node.span().startOffset() < node.span().endOffset()) {
        tokens.add(node);
      }
      return;
    }
    final boolean body = inBody || BODIES.contains(node.kind());
    for (final SyntaxNode child : node.children()) {
      collect(child, body, javadocs, tokens, declarations);
    }
  }

  /** The first non-empty token of a node, which is where a declaration's keywords begin. */
  private static SyntaxNode firstLeaf(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      if (child.span().startOffset() < child.span().endOffset()) {
        return firstLeaf(child);
      }
    }
    return node;
  }

  private static boolean isJavadoc(final SyntaxNode node) {
    return COMMENT.equals(node.kind()) && node.fields().getOrDefault("text", "").startsWith(JAVADOC);
  }

  /** The javadoc of a body: a marker that records it sits inside the body, so it is always invalid. */
  private static SyntaxNode bodyMarker(final SyntaxNode javadoc) {
    return new SyntaxNode(COMMENT, javadoc.span(), java.util.Map.of("body", "true"), List.of());
  }

  private boolean inBody(final SyntaxNode javadoc) {
    return "true".equals(javadoc.fields().get("body"));
  }

  /**
   * Whether the first token after the comment starts a declaration, possibly after leading
   * modifiers such as {@code export}, which the tree keeps outside the declaration node.
   */
  private static boolean startsDeclaration(
      final SyntaxNode javadoc, final List<SyntaxNode> tokens, final Set<Integer> declarations) {
    for (int index = 0; index < tokens.size(); index++) {
      final SyntaxNode token = tokens.get(index);
      if (token.span().startOffset() >= javadoc.span().endOffset()) {
        return declarations.contains(token.span().startOffset())
            || (MODIFIERS.contains(token.fields().getOrDefault("text", ""))
                && index + 1 < tokens.size()
                && declarations.contains(tokens.get(index + 1).span().startOffset()));
      }
    }
    return false;
  }
}
