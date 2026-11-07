package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an empty block of an if, else, while, for, do, try, finally, or static or instance
 * initializer. With {@code statement}, a block needs a statement; with {@code text}, an empty block
 * may hold a comment instead. Empty catch blocks are checked by the empty-catch-block rule.
 */
public final class EmptyBlockCheck implements UnitCheck {

  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String STATEMENT = "statement";
  private static final String FINALLY = "finallyBlock";
  private static final String CLASS_BODY_DECLARATION = "classBodyDeclaration";
  private static final Set<String> KEYWORDS = Set.of("'if'", "'while'", "'for'", "'do'", "'try'");
  private static final String TEXT = "text";

  private final boolean textAllowed;
  private final String message;

  /**
   * Creates the check.
   *
   * @param option {@code statement} or {@code text}
   * @param message the message
   */
  public EmptyBlockCheck(final String option, final String message) {
    if (!TEXT.equals(option) && !"statement".equals(option)) {
      throw new IllegalArgumentException("option must be statement or text, not " + option);
    }
    this.textAllowed = TEXT.equals(option);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode block : candidates(root)) {
      if (isEmpty(block, file.text())) {
        matches.add(CheckSupport.at(block.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private List<SyntaxNode> candidates(final SyntaxNode root) {
    final List<SyntaxNode> blocks = new ArrayList<>();
    for (final SyntaxNode node : allNodes(root)) {
      if (STATEMENT.equals(node.kind())
          && !node.children().isEmpty()
          && KEYWORDS.contains(node.children().get(0).kind())) {
        node.children().forEach(child -> addBody(child, blocks));
      } else if (FINALLY.equals(node.kind()) || CLASS_BODY_DECLARATION.equals(node.kind())) {
        node.children().forEach(child -> addBlock(child, blocks));
      }
    }
    return blocks;
  }

  private static List<SyntaxNode> allNodes(final SyntaxNode root) {
    final List<SyntaxNode> nodes = new ArrayList<>();
    nodes.add(root);
    nodes.addAll(SyntaxWalk.descendants(root));
    return nodes;
  }

  private static void addBody(final SyntaxNode child, final List<SyntaxNode> blocks) {
    if (STATEMENT.equals(child.kind()) && !child.children().isEmpty()) {
      addBlock(child.children().get(0), blocks);
    } else {
      addBlock(child, blocks);
    }
  }

  private static void addBlock(final SyntaxNode child, final List<SyntaxNode> blocks) {
    if (BLOCK.equals(child.kind())) {
      blocks.add(child);
    }
  }

  private boolean isEmpty(final SyntaxNode block, final String text) {
    if (block.children().stream().anyMatch(child -> BLOCK_STATEMENT.equals(child.kind()))) {
      return false;
    }
    return !textAllowed || !hasComment(block, text);
  }

  private static boolean hasComment(final SyntaxNode block, final String text) {
    final int start = block.span().startOffset();
    final int end = Math.min(block.span().endOffset(), text.length());
    return start < end
        && (text.substring(start, end).contains("//") || text.substring(start, end).contains("/*"));
  }
}
