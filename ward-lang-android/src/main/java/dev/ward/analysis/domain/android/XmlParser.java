package dev.ward.analysis.domain.android;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the XML of an Android manifest or resource file into a {@link SyntaxNode} tree with exact
 * positions. It reads elements, attributes, text, CDATA, comments, processing instructions and the
 * five predefined and the numeric character references. It rejects a DOCTYPE, so no entity is ever
 * expanded or fetched, and it never throws: a problem is a diagnostic next to an empty document.
 */
public final class XmlParser {

  private static final int MAX_DEPTH = 200;
  private static final char BOM = '﻿';
  private static final char TAG_OPEN = '<';
  private static final String COMMENT_END = "-->";
  private static final String COMMENT_START = "<!--";
  private static final String PI_END = "?>";
  private static final String SELF_CLOSE = "/>";

  private final XmlCursor cursor;
  private final XmlPositions positions;
  private final XmlAttributes attributes;
  private final XmlText text;
  private int depth;

  private XmlParser(final String source) {
    this.cursor = new XmlCursor(source);
    this.positions = new XmlPositions(source);
    this.attributes = new XmlAttributes(cursor, positions);
    this.text = new XmlText(cursor, positions);
  }

  /**
   * Reads an XML text.
   *
   * @param text the text of the file
   * @return the tree, and a diagnostic when the text is not well formed
   */
  public static XmlResult parse(final String text) {
    final XmlParser parser = new XmlParser(text);
    try {
      return new XmlResult(parser.document(), List.of());
    } catch (final XmlSyntaxException problem) {
      final int offset = Math.min(problem.offset(), text.length());
      final XmlDiagnostic diagnostic =
          new XmlDiagnostic(
              problem.getMessage(), parser.positions.line(offset), parser.positions.column(offset));
      final SyntaxNode empty =
          new SyntaxNode(AndroidSyntax.DOCUMENT, parser.positions.span(0, 0), Map.of(), List.of());
      return new XmlResult(empty, List.of(diagnostic));
    }
  }

  private SyntaxNode document() {
    if (cursor.at(BOM)) {
      cursor.advance(1);
    }
    skipMisc();
    if (!cursor.at(TAG_OPEN)) {
      throw cursor.problem("expected the root element");
    }
    final SyntaxNode root = element();
    skipMisc();
    if (!cursor.atEnd()) {
      throw cursor.problem("unexpected content after the root element");
    }
    return root;
  }

  /** Skips white space, comments, processing instructions and the XML declaration. */
  private void skipMisc() {
    boolean more = true;
    while (more) {
      XmlTokens.skipSpace(cursor);
      if (cursor.startsWith("<?")) {
        XmlTokens.skipPast(cursor, PI_END, "unterminated processing instruction");
      } else if (cursor.startsWith(COMMENT_START)) {
        XmlTokens.skipPast(cursor, COMMENT_END, "unterminated comment");
      } else if (cursor.startsWith("<!DOCTYPE")) {
        throw cursor.problem("a DOCTYPE is not supported");
      } else {
        more = false;
      }
    }
  }

  private SyntaxNode element() {
    depth++;
    if (depth > MAX_DEPTH) {
      throw cursor.problem("elements are nested too deeply");
    }
    final int start = cursor.position();
    cursor.advance(1);
    final String name = XmlTokens.name(cursor);
    final List<SyntaxNode> children = new ArrayList<>(attributes.read());
    if (cursor.startsWith(SELF_CLOSE)) {
      cursor.advance(SELF_CLOSE.length());
    } else {
      cursor.advance(1);
      content(name, children);
    }
    depth--;
    return new SyntaxNode(
        AndroidSyntax.ELEMENT,
        positions.span(start, cursor.position()),
        Map.of(AndroidSyntax.NAME, name),
        children);
  }

  /** Reads the content of an element up to and including its end tag. */
  private void content(final String name, final List<SyntaxNode> children) {
    while (!cursor.startsWith("</")) {
      if (cursor.atEnd()) {
        throw cursor.problem("unterminated element " + name);
      }
      contentItem(children);
    }
    endTag(name);
  }

  private void contentItem(final List<SyntaxNode> children) {
    if (cursor.startsWith(COMMENT_START)) {
      XmlTokens.skipPast(cursor, COMMENT_END, "unterminated comment");
    } else if (cursor.startsWith("<?")) {
      XmlTokens.skipPast(cursor, PI_END, "unterminated processing instruction");
    } else if (cursor.startsWith(XmlText.CDATA_START)) {
      text.cdata(children);
    } else if (cursor.at(TAG_OPEN)) {
      children.add(element());
    } else {
      text.characters(children);
    }
  }

  private void endTag(final String name) {
    final int start = cursor.position();
    cursor.advance(2);
    if (!XmlTokens.name(cursor).equals(name)) {
      throw new XmlSyntaxException("end tag does not match " + name, start);
    }
    XmlTokens.skipSpace(cursor);
    XmlTokens.expect(cursor, '>');
  }
}
