package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Links the KDoc comments of a Kotlin tree to the declarations around them. The parser places a
 * comment among the children of the node that precedes it, so the links follow the order of the
 * leaves in the source, not the order of the siblings.
 */
final class KotlinDocs {

  /** The kinds of the declarations that a KDoc comment documents. */
  static final Set<String> DECLARATIONS =
      Set.of(
          "classDeclaration",
          "objectDeclaration",
          "functionDeclaration",
          "propertyDeclaration",
          "secondaryConstructor",
          "typeAlias",
          "enumEntry");

  private static final Set<String> STARTS =
      Set.of(
          "modifierList",
          "annotation",
          "classDeclaration",
          "objectDeclaration",
          "functionDeclaration",
          "propertyDeclaration",
          "secondaryConstructor",
          "typeAlias",
          "enumEntry");
  private static final Set<String> KEYWORDS_BEFORE_DECLARATIONS = Set.of("package", "import");
  private static final String TEXT = "text";
  private static final String KDOC_OPEN = "/**";
  private static final String EMPTY_BLOCK = "/**/";
  private static final String SEMICOLON = ";";

  private final List<SyntaxNode> leaves;
  private final Map<Integer, Integer> indexByOffset = new HashMap<>();
  private final Set<Integer> starts = new HashSet<>();

  /**
   * Indexes the tree.
   *
   * @param root the root of the Kotlin tree
   */
  KotlinDocs(final SyntaxNode root) {
    this.leaves = new ArrayList<>();
    collectTokens(root, this.leaves);
    for (int index = 0; index < leaves.size(); index++) {
      final SyntaxNode leaf = leaves.get(index);
      if (!text(leaf).isEmpty()) {
        indexByOffset.putIfAbsent(leaf.span().startOffset(), index);
      }
    }
    collectStarts(root);
  }

  /** Returns whether {@code node} is a KDoc comment, the {@code /** ... *}{@code /} form. */
  static boolean isKdoc(final SyntaxNode node) {
    final String text = text(node);
    return SyntaxWalk.COMMENT_KIND.equals(node.kind())
        && text.startsWith(KDOC_OPEN)
        && !EMPTY_BLOCK.equals(text);
  }

  /** Returns the KDoc comments of the tree, in source order. */
  List<SyntaxNode> kdocs() {
    return leaves.stream().filter(KotlinDocs::isKdoc).toList();
  }

  /** Returns the KDoc comment that directly precedes {@code declaration}, if there is one. */
  Optional<SyntaxNode> documenting(final SyntaxNode declaration) {
    final Integer index = indexByOffset.get(declaration.span().startOffset());
    if (index == null) {
      return Optional.empty();
    }
    for (int before = index - 1; before >= 0; before--) {
      final SyntaxNode leaf = leaves.get(before);
      if (!isBlank(leaf)) {
        return isKdoc(leaf) ? Optional.of(leaf) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  /** Returns whether the token after {@code comment} starts a declaration or a file header. */
  boolean precedesDeclaration(final SyntaxNode comment) {
    final Integer index = indexByOffset.get(comment.span().startOffset());
    if (index == null) {
      return false;
    }
    for (int after = index + 1; after < leaves.size(); after++) {
      final SyntaxNode leaf = leaves.get(after);
      if (!isBlank(leaf) && !SyntaxWalk.COMMENT_KIND.equals(leaf.kind())) {
        return starts.contains(leaf.span().startOffset())
            || KEYWORDS_BEFORE_DECLARATIONS.contains(text(leaf));
      }
    }
    return false;
  }

  /** Collects the tokens and comments below {@code node}, which SyntaxWalk.leaves leaves out. */
  private static void collectTokens(final SyntaxNode node, final List<SyntaxNode> out) {
    if (node.children().isEmpty()) {
      if (node.fields().containsKey(TEXT)) {
        out.add(node);
      }
      return;
    }
    node.children().forEach(child -> collectTokens(child, out));
  }

  private void collectStarts(final SyntaxNode node) {
    if (STARTS.contains(node.kind()) && !node.children().isEmpty()) {
      starts.add(node.span().startOffset());
    }
    node.children().forEach(this::collectStarts);
  }

  /** Returns the text of a leaf, or an empty string for a rule node with no tokens. */
  static String text(final SyntaxNode node) {
    final String text = node.fields().get(TEXT);
    return text == null ? "" : text;
  }

  private static boolean isBlank(final SyntaxNode leaf) {
    final String text = text(leaf);
    return text.isBlank() || SEMICOLON.equals(text);
  }

  /** Returns the children of {@code node} of {@code kind}, as a new list. */
  static List<SyntaxNode> childrenOf(final SyntaxNode node, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (kind.equals(child.kind())) {
        found.add(child);
      }
    }
    return found;
  }
}
