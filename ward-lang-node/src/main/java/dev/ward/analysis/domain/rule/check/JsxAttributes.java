package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The attributes of one JSX opening or self-closing element, shared by the Biome JSX checks. A
 * spread attribute is not an attribute here: it is a separate node the checks do not read.
 */
final class JsxAttributes {

  /** The node kinds of an element that carries attributes. */
  static final String OPENING = "jsxOpeningElement";

  static final String SELF_CLOSING = "jsxSelfClosingElement";

  private static final String ATTRIBUTE = "jsxAttribute";
  private static final String VALUE = "jsxAttributeValue";
  private static final String NAME = "jsxElementName";
  private static final String TOKEN = "JsxIdentifier";
  private static final String COLON = ":";

  private JsxAttributes() {}

  /** The attribute nodes of an element, in source order. */
  static List<SyntaxNode> of(final SyntaxNode element) {
    return element.children().stream().filter(child -> ATTRIBUTE.equals(child.kind())).toList();
  }

  /** The element's name as written, such as {@code a} or {@code ui.Input}; empty when unknown. */
  static String elementName(final SyntaxNode element) {
    return element.children().stream()
        .filter(child -> NAME.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  /** The attribute's name as written, namespaced names joined by a colon, such as {@code id}. */
  static String name(final SyntaxNode attribute) {
    return attribute.children().stream()
        .filter(child -> TOKEN.equals(child.kind()))
        .map(SyntaxWalk::text)
        .collect(Collectors.joining(COLON));
  }

  /** The attribute's value node, if it has one. */
  static Optional<SyntaxNode> value(final SyntaxNode attribute) {
    return attribute.children().stream().filter(child -> VALUE.equals(child.kind())).findFirst();
  }

  /** The text of a string literal value without its quotes, or empty for any other value. */
  static Optional<String> literal(final SyntaxNode attribute) {
    return value(attribute)
        .map(SyntaxWalk::text)
        .filter(text -> text.length() >= 2)
        .filter(text -> isQuote(text.charAt(0)) && text.charAt(text.length() - 1) == text.charAt(0))
        .map(text -> text.substring(1, text.length() - 1));
  }

  /**
   * The whitespace-separated tokens of a static attribute value, read from the source text so that
   * the spaces survive; empty for a dynamic value. Biome's {@code from_roles} reads a role this
   * way.
   */
  static List<String> tokens(final SyntaxNode attribute, final String source) {
    return value(attribute)
        .filter(node -> literal(attribute).isPresent())
        .map(node -> source.substring(node.span().startOffset(), node.span().endOffset()))
        .map(raw -> raw.substring(1, raw.length() - 1).trim())
        .filter(inner -> !inner.isEmpty())
        .map(inner -> List.of(inner.split("\\s+")))
        .orElse(List.of());
  }

  private static boolean isQuote(final char character) {
    return character == '"' || character == '\'';
  }
}
