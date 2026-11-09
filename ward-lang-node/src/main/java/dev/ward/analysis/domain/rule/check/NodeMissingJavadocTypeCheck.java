package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports each class, interface, enum or type alias declaration that has no javadoc comment
 * directly before it, like Checkstyle's MissingJavadocType. With {@code publicOnly} the check
 * covers exported declarations only, the Javadoc {@code public} scope. The keywords between the
 * comment and the declaration ({@code export}, {@code default}, {@code declare}, {@code abstract})
 * are skipped.
 */
public final class NodeMissingJavadocTypeCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final String EXPORT = "export";
  private static final String JAVADOC = "/**";
  private static final Set<String> TYPES =
      Set.of("classDeclaration", "interfaceDeclaration", "enumDeclaration", "typeAliasDeclaration");
  private static final Set<String> KEYWORDS = Set.of(EXPORT, "default", "declare", "abstract");

  private final boolean publicOnly;
  private final String message;

  /**
   * Creates the check.
   *
   * @param publicOnly whether only exported declarations are checked
   * @param message the message of each finding
   */
  public NodeMissingJavadocTypeCheck(final boolean publicOnly, final String message) {
    this.publicOnly = publicOnly;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> leaves = new ArrayList<>();
    final List<SyntaxNode> declarations = new ArrayList<>();
    collect(root, leaves, declarations);
    leaves.sort(Comparator.comparingInt(leaf -> leaf.span().startOffset()));
    final Map<Integer, Integer> index = new HashMap<>();
    for (int position = 0; position < leaves.size(); position++) {
      index.put(leaves.get(position).span().startOffset(), position);
    }
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode declaration : declarations) {
      final int start = NodeSyntax.firstLeaf(declaration).span().startOffset();
      final Integer position = index.get(start);
      if (position != null && !documented(leaves, position) && !skipped(leaves, position)) {
        found.add(new RuleMatch(declaration.span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Gathers the leaves, comments as single leaves, and the type declarations. */
  private static void collect(
      final SyntaxNode node, final List<SyntaxNode> leaves, final List<SyntaxNode> declarations) {
    if (TYPES.contains(node.kind())) {
      declarations.add(node);
    }
    if (COMMENT.equals(node.kind()) || node.children().isEmpty()) {
      if (node.span().startOffset() < node.span().endOffset()) {
        leaves.add(node);
      }
      return;
    }
    node.children().forEach(child -> collect(child, leaves, declarations));
  }

  /** Whether the keywords before the declaration are preceded by a javadoc comment. */
  private static boolean documented(final List<SyntaxNode> leaves, final int position) {
    final int before = keywordsStart(leaves, position) - 1;
    return before >= 0 && javadoc(leaves.get(before));
  }

  /** Whether the declaration is exported, so it is checked by the {@code public} scope. */
  private boolean skipped(final List<SyntaxNode> leaves, final int position) {
    return publicOnly && !exported(leaves, position);
  }

  private static boolean exported(final List<SyntaxNode> leaves, final int position) {
    for (int index = position - 1; index >= keywordsStart(leaves, position); index--) {
      if (EXPORT.equals(text(leaves.get(index)))) {
        return true;
      }
    }
    return false;
  }

  /** The index of the first keyword of the run that ends just before the declaration. */
  private static int keywordsStart(final List<SyntaxNode> leaves, final int position) {
    int start = position;
    while (start > 0 && KEYWORDS.contains(text(leaves.get(start - 1)))) {
      start--;
    }
    return start;
  }

  private static boolean javadoc(final SyntaxNode leaf) {
    return COMMENT.equals(leaf.kind()) && text(leaf).startsWith(JAVADOC);
  }

  private static String text(final SyntaxNode leaf) {
    return COMMENT.equals(leaf.kind())
        ? leaf.fields().getOrDefault("text", "")
        : NodeSyntax.text(leaf);
  }
}
