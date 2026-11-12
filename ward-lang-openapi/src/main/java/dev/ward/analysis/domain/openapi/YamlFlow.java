package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads flow collections ({@code [a, b]} and {@code {a: b}}), which is all JSON uses. */
final class YamlFlow {

  private static final int MAX_DEPTH = 256;
  private static final char ANCHOR = '&';
  private static final char ALIAS = '*';
  private static final char TAG = '!';
  private static final char SEQUENCE_START = '[';
  private static final char SEQUENCE_END = ']';
  private static final char MAPPING_START = '{';
  private static final char MAPPING_END = '}';
  private static final char DOUBLE = '"';
  private static final char SINGLE = '\'';
  private static final char COLON = ':';
  private static final char COMMA = ',';
  private static final String UNSUPPORTED_KEYS = "[{*&!";

  private final YamlCursor cursor;
  private final YamlLines lines;
  private final YamlScalars scalars;
  private final YamlAnchors anchors;
  private final YamlFlowText text;
  private int depth;

  YamlFlow(
      final YamlCursor cursor,
      final YamlLines lines,
      final YamlScalars scalars,
      final YamlAnchors anchors) {
    this.cursor = cursor;
    this.lines = lines;
    this.scalars = scalars;
    this.anchors = anchors;
    this.text = new YamlFlowText(cursor, lines);
  }

  /** Reads one flow node at the cursor, skipping the space before it. */
  SyntaxNode node() {
    text.skipSpace();
    depth++;
    try {
      if (depth > MAX_DEPTH) {
        throw cursor.fail("the document is nested too deeply", cursor.pos());
      }
      return value();
    } finally {
      depth--;
    }
  }

  private SyntaxNode value() {
    final int start = cursor.pos();
    final char c = cursor.peek();
    final SyntaxNode node;
    if (c == ANCHOR) {
      final String name = YamlAnchors.name(cursor);
      node = node();
      anchors.define(name, node);
    } else if (c == ALIAS) {
      final String name = YamlAnchors.name(cursor);
      node = anchors.expand(name, lines.span(start, cursor.pos()), cursor);
    } else if (c == SEQUENCE_START) {
      node = sequence();
    } else if (c == MAPPING_START) {
      node = mapping();
    } else if (c == DOUBLE) {
      node = quoted(start, scalars.doubleQuoted());
    } else if (c == SINGLE) {
      node = quoted(start, scalars.singleQuoted());
    } else if (c == TAG) {
      throw cursor.fail("tags are not supported", start);
    } else {
      node = plain();
    }
    return node;
  }

  private SyntaxNode quoted(final int start, final String value) {
    return YamlNodes.scalar(YamlSyntax.STRING, lines.span(start, cursor.pos()), value);
  }

  private SyntaxNode sequence() {
    final int start = cursor.pos();
    cursor.advance(1);
    final List<SyntaxNode> items = new ArrayList<>();
    while (true) {
      text.skipSpace();
      if (cursor.peek() == SEQUENCE_END) {
        cursor.advance(1);
        return YamlNodes.array(items, lines.span(start, cursor.pos()));
      }
      items.add(node());
      text.skipSpace();
      if (cursor.peek() == COLON) {
        throw cursor.fail("a single pair inside a sequence is not supported", cursor.pos());
      }
      separator(SEQUENCE_END, "expected ',' or ']'");
    }
  }

  private SyntaxNode mapping() {
    final int start = cursor.pos();
    cursor.advance(1);
    final YamlEntries entries = new YamlEntries(cursor, lines);
    while (true) {
      text.skipSpace();
      if (cursor.peek() == MAPPING_END) {
        cursor.advance(1);
        return entries.build(lines.span(start, cursor.pos()));
      }
      entry(entries);
      text.skipSpace();
      separator(MAPPING_END, "expected ',' or '}'");
    }
  }

  /** Moves past the comma after an entry, or leaves the closing character for the caller. */
  private void separator(final char close, final String message) {
    if (cursor.peek() == COMMA) {
      cursor.advance(1);
    } else if (cursor.peek() != close) {
      throw cursor.fail(message, cursor.pos());
    }
  }

  private void entry(final YamlEntries entries) {
    final SyntaxNode key = key();
    text.skipSpace();
    final SyntaxNode value;
    if (cursor.peek() == COLON) {
      cursor.advance(1);
      text.skipSpace();
      value = YamlFlowText.isEnd(cursor.peek()) ? nullHere() : node();
    } else {
      value = nullHere();
    }
    entries.add(key, value);
  }

  private SyntaxNode key() {
    final int keyStart = cursor.pos();
    final char first = cursor.peek();
    final String name;
    if (first == DOUBLE) {
      name = scalars.doubleQuoted();
    } else if (first == SINGLE) {
      name = scalars.singleQuoted();
    } else if (UNSUPPORTED_KEYS.indexOf(first) >= 0) {
      throw cursor.fail("this kind of key is not supported", keyStart);
    } else {
      name = text.read();
      if (name.isEmpty()) {
        throw cursor.fail("expected a key", keyStart);
      }
    }
    return YamlNodes.key(lines.span(keyStart, cursor.pos()), name);
  }

  private SyntaxNode plain() {
    final int start = cursor.pos();
    final String value = text.read();
    return YamlNodes.scalar(YamlScalars.kindOf(value), lines.span(start, cursor.pos()), value);
  }

  private SyntaxNode nullHere() {
    return YamlNodes.nullAt(lines.span(cursor.pos(), cursor.pos()));
  }
}
