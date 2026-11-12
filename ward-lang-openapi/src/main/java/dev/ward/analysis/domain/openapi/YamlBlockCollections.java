package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads block mappings and block sequences, which nest by indentation. */
final class YamlBlockCollections {

  private static final String MERGE = "<<";
  private static final char SINGLE = '\'';
  private static final char DOUBLE = '"';
  private static final char COLON = ':';

  private final YamlCursor cursor;
  private final YamlLines lines;
  private final YamlScalars scalars;
  private final YamlKeys keys;
  private final YamlNodeReader nodes;

  YamlBlockCollections(
      final YamlCursor cursor,
      final YamlLines lines,
      final YamlScalars scalars,
      final YamlKeys keys,
      final YamlNodeReader nodes) {
    this.cursor = cursor;
    this.lines = lines;
    this.scalars = scalars;
    this.keys = keys;
    this.nodes = nodes;
  }

  /**
   * Reads the block sequence whose first {@code -} is at the cursor, at indentation {@code indent}.
   */
  SyntaxNode sequence(final int indent) {
    final List<SyntaxNode> items = new ArrayList<>();
    final int start = cursor.pos();
    do {
      cursor.advance(1);
      items.add(item(indent));
    } while (nextAt(indent) && keys.sequenceEntryAt(cursor.pos()));
    return YamlNodes.array(
        items, YamlNodes.cover(lines.span(start, start + 1), items.getLast().span()));
  }

  /** Reads the block mapping whose first key is at the cursor, at indentation {@code indent}. */
  SyntaxNode mapping(final int indent) {
    final YamlEntries entries = new YamlEntries(cursor, lines);
    do {
      addEntry(entries, indent);
    } while (mappingContinues(indent));
    return entries.build(null);
  }

  private SyntaxNode item(final int indent) {
    if (lines.skipToLineEnd()) {
      final int after = cursor.pos();
      lines.skipBlank();
      if (cursor.eof() || lines.indent() <= indent || lines.atDocumentMarker()) {
        return YamlNodes.nullAt(lines.span(after, after));
      }
    }
    return nodes.read(indent, true);
  }

  private void addEntry(final YamlEntries entries, final int indent) {
    final int keyStart = cursor.pos();
    final boolean plainKey = cursor.peek() != DOUBLE && cursor.peek() != SINGLE;
    final SyntaxNode key = key();
    final SyntaxNode value = value(indent);
    if (plainKey && MERGE.equals(key.fields().get(YamlSyntax.VALUE))) {
      entries.merge(value, keyStart);
    } else {
      entries.add(key, value);
    }
  }

  private boolean mappingContinues(final int indent) {
    if (!nextAt(indent)) {
      return false;
    }
    if (keys.sequenceEntryAt(cursor.pos())) {
      throw cursor.fail("a sequence entry cannot follow a mapping at the same level", cursor.pos());
    }
    return true;
  }

  /** Moves to the next line with content; tells whether it is another entry at {@code indent}. */
  private boolean nextAt(final int indent) {
    lines.skipBlank();
    if (cursor.eof() || lines.atDocumentMarker() || lines.indent() < indent) {
      return false;
    }
    if (lines.indent() > indent) {
      throw cursor.fail("unexpected indentation", cursor.pos());
    }
    return true;
  }

  /** Reads a key and the colon after it, and leaves the cursor after the colon. */
  private SyntaxNode key() {
    final int start = cursor.pos();
    final char c = cursor.peek();
    final boolean quoted = c == DOUBLE || c == SINGLE;
    final String name = quoted ? quotedKey(c) : plainKey(start);
    final int end = quoted ? cursor.pos() : start + name.length();
    if (quoted) {
      lines.skipSpaces();
    } else {
      cursor.moveTo(keys.plainKeyColon(start));
    }
    if (cursor.peek() != COLON) {
      throw cursor.fail("expected ':' after the key", cursor.pos());
    }
    cursor.advance(1);
    return quoted
        ? YamlNodes.key(lines.span(start, end), name)
        : YamlNodes.plainKey(lines.span(start, end), name);
  }

  private String quotedKey(final char quote) {
    return quote == DOUBLE ? scalars.doubleQuoted() : scalars.singleQuoted();
  }

  /** The text of the plain key at {@code start}, without the spaces before its colon. */
  private String plainKey(final int start) {
    final int colon = keys.plainKeyColon(start);
    if (colon < 0) {
      throw cursor.fail("expected a key followed by ':'", start);
    }
    int trimmed = colon;
    while (trimmed > start && cursor.charAt(trimmed - 1) == ' ') {
      trimmed--;
    }
    return cursor.text().substring(start, trimmed);
  }

  private SyntaxNode value(final int indent) {
    lines.skipSpaces();
    if (!lines.skipToLineEnd()) {
      return nodes.read(indent, false);
    }
    final int after = cursor.pos();
    lines.skipBlank();
    final SyntaxNode empty = YamlNodes.nullAt(lines.span(after, after));
    if (cursor.eof() || lines.atDocumentMarker()) {
      return empty;
    }
    if (lines.indent() > indent) {
      return nodes.read(indent, true);
    }
    if (lines.indent() == indent && keys.sequenceEntryAt(cursor.pos())) {
      return sequence(indent);
    }
    return empty;
  }
}
