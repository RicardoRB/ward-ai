package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The truth of a JSX attribute, as Biome 2.5.15's {@code has_truthy_attribute} reads it: an
 * attribute with no value, or with a value that is not a static falsy one, is truthy, unless a
 * spread after it may override it. shortcut: a static value is read from the source text, which
 * covers the literals Biome treats as falsy (an empty or {@code false} string, {@code false},
 * {@code null}, {@code undefined} and {@code 0}); any other expression counts as truthy.
 */
final class JsxTruthiness {

  private static final String SPREAD = "jsxSpreadAttribute";
  private static final Set<String> FALSY_EXPRESSIONS = Set.of("false", "null", "undefined", "0");

  private JsxTruthiness() {}

  /** The first attribute with this name when it is truthy, empty otherwise. */
  static Optional<SyntaxNode> truthyAttribute(
      final SyntaxNode element, final String name, final String source) {
    final List<SyntaxNode> attributes = JsxAttributes.of(element);
    for (int index = 0; index < attributes.size(); index++) {
      final SyntaxNode attribute = attributes.get(index);
      if (name.equals(JsxAttributes.name(attribute))) {
        final boolean truthy = truthy(attribute, source) && !trailingSpread(attributes, index);
        return truthy ? Optional.of(attribute) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  /** The raw source text of an attribute's value, quotes or braces included. */
  static String raw(final SyntaxNode value, final String source) {
    return source.substring(value.span().startOffset(), value.span().endOffset());
  }

  private static boolean truthy(final SyntaxNode attribute, final String source) {
    return JsxAttributes.value(attribute).map(value -> !falsy(raw(value, source))).orElse(true);
  }

  private static boolean trailingSpread(final List<SyntaxNode> attributes, final int index) {
    return attributes.subList(index + 1, attributes.size()).stream()
        .anyMatch(attribute -> attribute.children().stream().anyMatch(JsxTruthiness::isSpread));
  }

  private static boolean isSpread(final SyntaxNode child) {
    return SPREAD.equals(child.kind());
  }

  private static boolean falsy(final String raw) {
    if (raw.startsWith("{") && raw.endsWith("}")) {
      final String inner = raw.substring(1, raw.length() - 1).trim();
      return FALSY_EXPRESSIONS.contains(inner) || falsyQuoted(inner);
    }
    return falsyQuoted(raw);
  }

  /** A quoted string that is empty or reads {@code false}. */
  private static boolean falsyQuoted(final String text) {
    if (!isQuoted(text)) {
      return false;
    }
    final String content = text.substring(1, text.length() - 1);
    return content.isEmpty() || "false".equals(content);
  }

  /** Whether the text is a string in single or double quotes. */
  static boolean isQuoted(final String text) {
    return text.length() >= 2
        && (text.charAt(0) == '"' || text.charAt(0) == '\'')
        && text.charAt(text.length() - 1) == text.charAt(0);
  }
}
