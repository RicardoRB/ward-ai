package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the dotted name at the start of a Kotlin expression or annotation, such as {@code
 * shop.util.formatPrice} of {@code shop.util.formatPrice(x)}. Reading stops at the first token that
 * cannot continue a name, such as a call, an index or an operator. A leading {@code @} of an
 * annotation label is skipped.
 */
final class KotlinNameChain {
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Set<String> HARD_KEYWORDS =
      Set.of(
          "as",
          "break",
          "class",
          "continue",
          "do",
          "else",
          "false",
          "for",
          "fun",
          "if",
          "in",
          "interface",
          "is",
          "null",
          "object",
          "package",
          "return",
          "super",
          "this",
          "throw",
          "true",
          "try",
          "typealias",
          "typeof",
          "val",
          "var",
          "when",
          "while");
  private static final String DOT = ".";
  private static final String AT = "@";

  private final List<String> segments = new ArrayList<>();
  private boolean expectIdentifier = true;

  private KotlinNameChain() {}

  /** Returns the dotted name at the start of {@code node}, or an empty string if it has none. */
  static String of(final SyntaxNode node) {
    final KotlinNameChain chain = new KotlinNameChain();
    chain.walk(node);
    return String.join(DOT, chain.segments);
  }

  /** Walks the leaves in order; returns true once the name has ended. */
  private boolean walk(final SyntaxNode node) {
    if (JavaSyntax.COMMENT.equals(node.kind())) {
      return false;
    }
    if (node.children().isEmpty()) {
      return accept(JavaSyntax.textOf(node).strip());
    }
    for (final SyntaxNode child : node.children()) {
      if (walk(child)) {
        return true;
      }
    }
    return false;
  }

  /** Accepts one token; returns true when it ends the name. Whitespace is skipped. */
  private boolean accept(final String text) {
    if (text.isEmpty()) {
      return false;
    }
    return expectIdentifier ? acceptIdentifier(text) : acceptSeparator(text);
  }

  /** After a name only a dot continues it. */
  private boolean acceptSeparator(final String text) {
    if (DOT.equals(text)) {
      expectIdentifier = true;
      return false;
    }
    return true;
  }

  private boolean acceptIdentifier(final String text) {
    if (segments.isEmpty() && AT.equals(text)) {
      return false;
    }
    final String name = segments.isEmpty() && text.startsWith(AT) ? text.substring(1) : text;
    if (!IDENTIFIER.matcher(name).matches() || HARD_KEYWORDS.contains(name)) {
      return true;
    }
    segments.add(name);
    expectIdentifier = false;
    return false;
  }
}
