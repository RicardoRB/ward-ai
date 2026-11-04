package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the dotted name at the start of a type position or expression, such as {@code Prices} of
 * {@code Prices.ZERO} or {@code shop.domain.Price} of a qualified type. Reading stops at the first
 * token that cannot continue a name, such as a type argument, a call or an operator, and skips
 * annotations, which are summarized on their own.
 */
final class JavaNameChain {
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final String DOT = ".";
  private static final String ANNOTATION = "annotation";

  private final List<String> segments = new ArrayList<>();
  private boolean lastWasIdentifier;

  private JavaNameChain() {}

  /** Returns the dotted name at the start of {@code node}, or an empty string if it has none. */
  static String of(final SyntaxNode node) {
    final JavaNameChain chain = new JavaNameChain();
    chain.walk(node);
    return chain.result();
  }

  /** Walks the leaves in order; returns true once the name has ended. */
  private boolean walk(final SyntaxNode node) {
    if (JavaSyntax.COMMENT.equals(node.kind()) || ANNOTATION.equals(node.kind())) {
      return false;
    }
    if (node.children().isEmpty()) {
      return accept(node);
    }
    for (final SyntaxNode child : node.children()) {
      if (walk(child)) {
        return true;
      }
    }
    return false;
  }

  /** Accepts one leaf; returns true when it ends the name. */
  private boolean accept(final SyntaxNode leaf) {
    final String text = JavaSyntax.textOf(leaf);
    if (isIdentifier(leaf, text)) {
      return acceptIdentifier(text);
    }
    if (DOT.equals(text) && started() && lastWasIdentifier) {
      lastWasIdentifier = false;
      return false;
    }
    return started();
  }

  private boolean acceptIdentifier(final String text) {
    if (started() && lastWasIdentifier) {
      return true;
    }
    segments.add(text);
    lastWasIdentifier = true;
    return false;
  }

  private boolean started() {
    return !segments.isEmpty();
  }

  private String result() {
    return String.join(DOT, segments);
  }

  /** Keywords and literals have quoted display names in the grammar, so they are not names. */
  private static boolean isIdentifier(final SyntaxNode leaf, final String text) {
    return !leaf.kind().startsWith("'") && IDENTIFIER.matcher(text).matches();
  }
}
