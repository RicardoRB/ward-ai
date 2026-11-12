package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds the {@link DocNode} tree of one file from the syntax tree {@link YamlParser} read. */
final class DocNodes {

  private DocNodes() {}

  static DocNode build(final String file, final SyntaxNode root) {
    return node(file, "", root, null);
  }

  private static DocNode node(
      final String file, final String pointer, final SyntaxNode syntax, final Span keySpan) {
    final List<DocNode.Entry> entries = new ArrayList<>();
    final List<DocNode> items = new ArrayList<>();
    final DocNode.Kind kind = kind(syntax.kind());
    if (kind == DocNode.Kind.OBJECT) {
      for (final SyntaxNode entry : syntax.children()) {
        final SyntaxNode key = entry.children().getFirst();
        final String name = key.fields().get(YamlSyntax.VALUE);
        final String child = pointer + "/" + name.replace("~", "~0").replace("/", "~1");
        entries.add(
            new DocNode.Entry(
                name,
                key.span(),
                node(file, child, entry.children().getLast(), key.span()),
                key.fields().containsKey(YamlSyntax.NUMERIC)));
      }
    } else if (kind == DocNode.Kind.ARRAY) {
      for (final SyntaxNode item : syntax.children()) {
        items.add(node(file, pointer + "/" + items.size(), item, null));
      }
    }
    final String text = syntax.fields().getOrDefault(YamlSyntax.VALUE, "");
    return new DocNode(
        kind, text, file, pointer, syntax.span(), Optional.ofNullable(keySpan), entries, items);
  }

  private static DocNode.Kind kind(final String syntax) {
    return switch (syntax) {
      case YamlSyntax.OBJECT -> DocNode.Kind.OBJECT;
      case YamlSyntax.ARRAY -> DocNode.Kind.ARRAY;
      case YamlSyntax.NUMBER -> DocNode.Kind.NUMBER;
      case YamlSyntax.BOOLEAN -> DocNode.Kind.BOOLEAN;
      case YamlSyntax.NULL -> DocNode.Kind.NULL;
      default -> DocNode.Kind.STRING;
    };
  }
}
