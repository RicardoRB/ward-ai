package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the tokens of a Kotlin tree in source order, with comments and newlines included, for the
 * ktlint checks that compare neighbouring comments and lines.
 */
final class KotlinLeaves {

  /** The kind of a newline token of the Kotlin grammar. */
  static final String NEWLINE = "NL";

  private static final String TEXT = "text";
  private static final String KDOC_START = "/**";
  private static final String EMPTY_BLOCK = "/**/";
  private static final String BLOCK_START = "/*";
  private static final String EOL_START = "//";
  private static final String KDOC = "kdoc";
  private static final String BLOCK = "block";
  private static final String EOL = "eol";

  private KotlinLeaves() {}

  /** Returns the leaves of {@code root} that carry text, comments included, in source order. */
  static List<SyntaxNode> tokens(final SyntaxNode root) {
    final List<SyntaxNode> out = new ArrayList<>();
    collect(root, out);
    return out;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> out) {
    if (node.children().isEmpty()) {
      if (node.fields().containsKey(TEXT)) {
        out.add(node);
      }
      return;
    }
    node.children().forEach(child -> collect(child, out));
  }

  /** Returns the text field of {@code node}, or the empty text. */
  static String text(final SyntaxNode node) {
    return node.fields().getOrDefault(TEXT, "");
  }

  /** Returns whether {@code node} is a comment of any kind. */
  static boolean isComment(final SyntaxNode node) {
    return "comment".equals(node.kind());
  }

  /** Returns whether {@code comment} is a KDoc, a block comment that starts with two stars. */
  static boolean isKDoc(final SyntaxNode comment) {
    final String text = text(comment);
    return text.startsWith(KDOC_START) && !EMPTY_BLOCK.equals(text);
  }

  /** Returns whether {@code comment} is a block comment that is not a KDoc. */
  static boolean isBlock(final SyntaxNode comment) {
    return text(comment).startsWith(BLOCK_START) && !isKDoc(comment);
  }

  /** Returns whether {@code comment} is an end-of-line comment. */
  static boolean isEol(final SyntaxNode comment) {
    return text(comment).startsWith(EOL_START);
  }

  /** Returns the comment type that the consecutive-comment rule compares: kdoc, block or eol. */
  static String commentType(final SyntaxNode comment) {
    if (isKDoc(comment)) {
      return KDOC;
    }
    return isBlock(comment) ? BLOCK : EOL;
  }

  /** Returns the position of the start of the file, which ktlint reports for file-level rules. */
  static Span fileStart() {
    return new Span(1, 1, 1, 1, 0, 0);
  }
}
