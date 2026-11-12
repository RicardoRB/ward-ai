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

  static SyntaxNode entry(final SyntaxNode key, final SyntaxNode value) {
    return new SyntaxNode(
        YamlSyntax.ENTRY, cover(key.span(), value.span()), Map.of(), List.of(key, value));
  }

  /** An object of {@code entries}, or of {@code empty} when there are none. */
  static SyntaxNode object(final List<SyntaxNode> entries, final Span empty) {
    return container(YamlSyntax.OBJECT, entries, empty);
  }

  /** An array of {@code items}, or of {@code empty} when there are none. */
  static SyntaxNode array(final List<SyntaxNode> items, final Span empty) {
    return container(YamlSyntax.ARRAY, items, empty);
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

  private static SyntaxNode container(
      final String kind, final List<SyntaxNode> children, final Span empty) {
    final Span span =
        children.isEmpty() ? empty : cover(children.getFirst().span(), children.getLast().span());
    return new SyntaxNode(kind, span, Map.of(), children);
  }

  private static Map<String, String> withAlias(
      final Map<String, String> fields, final String name, final String origin) {
    final Map<String, String> copy = new HashMap<>(fields);
    copy.put(YamlSyntax.ALIAS, name);
    copy.put(YamlSyntax.ORIGIN, origin);
    return copy;
  }
}
