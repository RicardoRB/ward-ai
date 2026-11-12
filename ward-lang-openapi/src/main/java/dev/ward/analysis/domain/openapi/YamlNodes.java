package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the nodes of the tree described by {@link YamlSyntax}. */
final class YamlNodes {

  private YamlNodes() {}

  static SyntaxNode scalar(final String kind, final Span span, final String value) {
    return new SyntaxNode(kind, span, Map.of(YamlSyntax.VALUE, value), List.of());
  }

  /** The null value of a key with nothing after it, as a zero-width node at {@code at}. */
  static SyntaxNode nullAt(final Span at) {
    return scalar(YamlSyntax.NULL, at, "");
  }

  static SyntaxNode key(final Span span, final String value) {
    return scalar(YamlSyntax.KEY, span, value);
  }

  /** A key written without quotes that YAML reads as a number, such as {@code 200:}. */
  static SyntaxNode plainKey(final Span span, final String value) {
    final SyntaxNode key = key(span, value);
    if (!YamlSyntax.NUMBER.equals(YamlScalars.kindOf(value))) {
      return key;
    }
    final Map<String, String> fields = new HashMap<>(key.fields());
    fields.put(YamlSyntax.NUMERIC, "true");
    return new SyntaxNode(key.kind(), key.span(), fields, key.children());
  }

  static SyntaxNode entry(final SyntaxNode key, final SyntaxNode value) {
    return new SyntaxNode(
        YamlSyntax.ENTRY, cover(key.span(), value.span()), Map.of(), List.of(key, value));
  }

  /** An object of {@code entries} that is written over {@code whole}. */
  static SyntaxNode object(final List<SyntaxNode> entries, final Span whole) {
    return new SyntaxNode(YamlSyntax.OBJECT, whole, Map.of(), entries);
  }

  /** An array of {@code items} that is written over {@code whole}. */
  static SyntaxNode array(final List<SyntaxNode> items, final Span whole) {
    return new SyntaxNode(YamlSyntax.ARRAY, whole, Map.of(), items);
  }

  /** The node as an expanded alias: it keeps the children and the alias site's position. */
  static SyntaxNode alias(final SyntaxNode original, final Span site, final String name) {
    final Span at = original.span();
    return new SyntaxNode(
        original.kind(),
        site,
        withAlias(original.fields(), name, at.startLine() + ":" + at.startColumn()),
        original.children());
  }

  /** The region from the start of {@code first} to the end of {@code last}. */
  static Span cover(final Span first, final Span last) {
    return new Span(
        first.startLine(),
        first.startColumn(),
        last.endLine(),
        last.endColumn(),
        first.startOffset(),
        last.endOffset());
  }

  private static Map<String, String> withAlias(
      final Map<String, String> fields, final String name, final String origin) {
    final Map<String, String> copy = new HashMap<>(fields);
    copy.put(YamlSyntax.ALIAS, name);
    copy.put(YamlSyntax.ORIGIN, origin);
    return copy;
  }
}
