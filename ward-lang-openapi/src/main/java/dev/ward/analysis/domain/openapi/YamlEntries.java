package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects the entries of one mapping: rejects duplicate keys and applies {@code <<} merges. */
final class YamlEntries {

  private final YamlCursor cursor;
  private final YamlLines lines;
  private final List<SyntaxNode> entries = new ArrayList<>();
  private final Map<String, Integer> index = new HashMap<>();
  private final Set<String> merged = new HashSet<>();

  YamlEntries(final YamlCursor cursor, final YamlLines lines) {
    this.cursor = cursor;
    this.lines = lines;
  }

  void add(final SyntaxNode key, final SyntaxNode value) {
    final String name = key.fields().get(YamlSyntax.VALUE);
    final Integer at = index.get(name);
    final SyntaxNode entry = YamlNodes.entry(key, value);
    if (at == null) {
      index.put(name, entries.size());
      entries.add(entry);
    } else if (merged.remove(name)) {
      entries.set(at, entry);
    } else {
      final int first = lines.line(entries.get(at).span().startOffset());
      throw cursor.fail(
          "duplicate key '" + name + "' (lines " + first + " and " + key.span().startLine() + ")",
          key.span().startOffset());
    }
  }

  /** Applies a {@code <<} merge of a mapping, or of a list of mappings, at offset {@code at}. */
  void merge(final SyntaxNode source, final int at) {
    if (YamlSyntax.OBJECT.equals(source.kind())) {
      source.children().forEach(this::mergeEntry);
    } else if (YamlSyntax.ARRAY.equals(source.kind())) {
      for (final SyntaxNode item : source.children()) {
        merge(requireObject(item, at), at);
      }
    } else {
      throw cursor.fail("a merge key needs a mapping or a list of mappings", at);
    }
  }

  /** The mapping of the entries, written over {@code whole} or, when null, from first to last. */
  SyntaxNode build(final Span whole) {
    final Span span =
        whole != null
            ? whole
            : YamlNodes.cover(entries.getFirst().span(), entries.getLast().span());
    return YamlNodes.object(entries, span);
  }

  private SyntaxNode requireObject(final SyntaxNode item, final int at) {
    if (!YamlSyntax.OBJECT.equals(item.kind())) {
      throw cursor.fail("a merge key needs a list of mappings", at);
    }
    return item;
  }

  private void mergeEntry(final SyntaxNode entry) {
    final String name = entry.children().getFirst().fields().get(YamlSyntax.VALUE);
    if (!index.containsKey(name)) {
      index.put(name, entries.size());
      entries.add(entry);
      merged.add(name);
    }
  }
}
