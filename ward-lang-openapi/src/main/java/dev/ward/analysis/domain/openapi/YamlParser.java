package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Reads YAML and JSON into the syntax tree of {@link YamlSyntax}, with the position of every key,
 * value and item. JSON is YAML, so one parser reads both and the file extension never decides the
 * format.
 *
 * <p>It reads the YAML subset that OpenAPI documents use: block and flow collections; plain,
 * single-quoted, double-quoted, literal and folded scalars; comments; anchors, aliases and the
 * {@code <<: *anchor} merge; the core schema for scalar types ({@code yes} and {@code no} are
 * text). It rejects, with a position, what it does not read: tags, complex keys, directives, more
 * than one document, duplicate keys, tabs as indentation, an alias before its anchor, and aliases
 * that expand past {@link YamlAnchors#MAX_EXPANDED} nodes. It never throws for a text: a problem is
 * a diagnostic, and the tree is then an empty document.
 *
 * <p>shortcut: an anchor on a mapping key (the {@code &a key: v} form) anchors the whole mapping,
 * and a single pair inside a flow sequence is rejected. Upgrade when a real specification needs
 * either.
 */
public final class YamlParser {

  private static final int MAX_DEPTH = 256;
  private static final String START = "---";
  private static final String END = "...";
  private static final char ANCHOR = '&';
  private static final char TAG = '!';

  private final YamlSource source;
  private final YamlCursor cursor;
  private final YamlLines lines;
  private final YamlAnchors anchors = new YamlAnchors();
  private final YamlBlockCollections blocks;
  private final YamlLeaves leaves;
  private final YamlKeys keys;
  private int depth;

  private YamlParser(final YamlSource source) {
    this.source = source;
    this.cursor = new YamlCursor(source);
    this.lines = new YamlLines(source, cursor);
    final YamlScalars scalars = new YamlScalars(cursor, lines);
    this.keys = new YamlKeys(cursor);
    this.blocks = new YamlBlockCollections(cursor, lines, scalars, keys, this::node);
    this.leaves = new YamlLeaves(cursor, lines, scalars, anchors, blocks);
  }

  /**
   * Reads one YAML or JSON document.
   *
   * @param text the file text, with a byte-order mark if the file has one
   * @return the tree, or an empty document and the first problem
   */
  public static YamlResult parse(final String text) {
    final YamlSource source = new YamlSource(text);
    final int binary = text.indexOf('\0');
    if (binary >= 0) {
      return YamlResult.failure(source, "the file is binary, not text", binary);
    }
    try {
      return new YamlResult(new YamlParser(source).document(), List.of());
    } catch (final YamlSyntaxException exception) {
      return YamlResult.failure(source, exception.getMessage(), exception.offset());
    }
  }

  private SyntaxNode document() {
    lines.skipBlank();
    if (cursor.peek() == '%' && lines.indent() == 0) {
      throw cursor.fail("YAML directives are not supported", cursor.pos());
    }
    if (marker(START)) {
      cursor.advance(START.length());
      if (lines.skipToLineEnd()) {
        lines.skipBlank();
      }
    }
    if (cursor.eof()) {
      throw cursor.fail("the document is empty", source.start());
    }
    final SyntaxNode root = node(-1, true);
    endOfDocument();
    return root;
  }

  /** Accepts an end marker and trailing blanks; anything else after the document is an error. */
  private void endOfDocument() {
    lines.skipBlank();
    if (marker(END)) {
      cursor.advance(END.length());
      lines.skipBlank();
    }
    if (!cursor.eof()) {
      throw cursor.fail(
          lines.atDocumentMarker()
              ? "more than one document in a file is not supported"
              : "unexpected content after the end of the document",
          cursor.pos());
    }
  }

  private boolean marker(final String text) {
    return lines.atDocumentMarker() && cursor.text().startsWith(text, cursor.pos());
  }

  /** Reads a node whose first character is at the cursor. */
  private SyntaxNode node(final int parentIndent, final boolean allowBlock) {
    depth++;
    try {
      if (depth > MAX_DEPTH) {
        throw cursor.fail("the document is nested too deeply", cursor.pos());
      }
      return properties(parentIndent, allowBlock);
    } finally {
      depth--;
    }
  }

  private SyntaxNode properties(final int parentIndent, final boolean sameLineBlock) {
    if (cursor.peek() != ANCHOR) {
      return untagged(parentIndent, sameLineBlock);
    }
    final String anchor = YamlAnchors.name(cursor);
    boolean allowBlock = sameLineBlock;
    if (lines.skipToLineEnd()) {
      final int after = cursor.pos();
      lines.skipBlank();
      if (isEmptyAnchor(parentIndent)) {
        return YamlNodes.nullAt(lines.span(after, after));
      }
      allowBlock = true;
    }
    final SyntaxNode node = untagged(parentIndent, allowBlock);
    anchors.define(anchor, node);
    return node;
  }

  /** Tells whether an anchor on its own line has no node under it. */
  private boolean isEmptyAnchor(final int parentIndent) {
    return cursor.eof() || lines.indent() <= parentIndent || lines.atDocumentMarker();
  }

  private SyntaxNode untagged(final int parentIndent, final boolean allowBlock) {
    if (cursor.peek() == TAG) {
      throw cursor.fail("tags are not supported", cursor.pos());
    }
    return body(parentIndent, allowBlock);
  }

  private SyntaxNode body(final int parentIndent, final boolean allowBlock) {
    final int start = cursor.pos();
    if (keys.sequenceEntryAt(start)) {
      YamlLeaves.requireBlock(cursor, allowBlock, "a sequence entry is not allowed here");
      return blocks.sequence(lines.indent());
    }
    if (cursor.peek() == '?' && cursor.isBlankAt(start + 1)) {
      throw cursor.fail("complex mapping keys are not supported", start);
    }
    return leaves.read(parentIndent, allowBlock);
  }
}
