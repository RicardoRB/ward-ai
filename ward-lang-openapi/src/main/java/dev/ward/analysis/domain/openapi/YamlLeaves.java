package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** Reads the nodes that are not block collections: aliases, scalars and flow collections. */
final class YamlLeaves {

  private static final char SINGLE = '\'';
  private static final char DOUBLE = '"';
  private static final char ALIAS = '*';

  private final YamlCursor cursor;
  private final YamlLines lines;
  private final YamlScalars scalars;
  private final YamlAnchors anchors;
  private final YamlFlow flow;
  private final YamlBlockScalar blockScalars;
  private final YamlPlain plain;
  private final YamlKeys keys;
  private final YamlBlockCollections blocks;

  YamlLeaves(
      final YamlCursor cursor,
      final YamlLines lines,
      final YamlScalars scalars,
      final YamlAnchors anchors,
      final YamlBlockCollections blocks) {
    this.cursor = cursor;
    this.lines = lines;
    this.scalars = scalars;
    this.anchors = anchors;
    this.keys = new YamlKeys(cursor);
    this.blocks = blocks;
    this.flow = new YamlFlow(cursor, lines, scalars, anchors);
    this.blockScalars = new YamlBlockScalar(cursor, lines);
    this.plain = new YamlPlain(cursor, lines, this.keys);
  }

  /** Reads an alias, a block scalar, a flow collection, a quoted or plain scalar, or a mapping. */
  SyntaxNode read(final int parentIndent, final boolean allowBlock) {
    final char c = cursor.peek();
    if (c == ALIAS) {
      return alias();
    }
    if (c == '|' || c == '>') {
      return blockScalar(parentIndent);
    }
    if (c == '[' || c == '{') {
      return flowCollection();
    }
    return scalarOrMapping(parentIndent, allowBlock);
  }

  /** Fails when a block collection starts where only a value on the key's line may. */
  static void requireBlock(
      final YamlCursor cursor, final boolean allowBlock, final String message) {
    if (!allowBlock) {
      throw cursor.fail(message, cursor.pos());
    }
  }

  private SyntaxNode blockScalar(final int parentIndent) {
    final int start = cursor.pos();
    final YamlBlockScalar.Block block = blockScalars.read(parentIndent);
    return YamlNodes.scalar(YamlSyntax.STRING, lines.span(start, block.end()), block.text());
  }

  private SyntaxNode flowCollection() {
    final SyntaxNode collection = flow.node();
    endOfValue();
    return collection;
  }

  private SyntaxNode scalarOrMapping(final int parentIndent, final boolean allowBlock) {
    final int start = cursor.pos();
    final char c = cursor.peek();
    final boolean quoted = c == DOUBLE || c == SINGLE;
    final boolean key = quoted ? keys.quotedKeyColon(start) >= 0 : keys.plainKeyColon(start) >= 0;
    if (key) {
      requireBlock(cursor, allowBlock, "a mapping is not allowed here");
      return blocks.mapping(lines.indent());
    }
    if (quoted) {
      final String text = c == DOUBLE ? scalars.doubleQuoted() : scalars.singleQuoted();
      final SyntaxNode node =
          YamlNodes.scalar(YamlSyntax.STRING, lines.span(start, cursor.pos()), text);
      endOfValue();
      return node;
    }
    return plain.read(parentIndent);
  }

  private SyntaxNode alias() {
    final int start = cursor.pos();
    final String name = YamlAnchors.name(cursor);
    final SyntaxNode node = anchors.expand(name, lines.span(start, cursor.pos()), cursor);
    endOfValue();
    return node;
  }

  private void endOfValue() {
    if (!lines.skipToLineEnd()) {
      throw cursor.fail("unexpected text after the value", cursor.pos());
    }
  }
}
