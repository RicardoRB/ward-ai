package dev.ward.analysis.domain.android;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/** Reads the character data and CDATA sections between elements. */
final class XmlText {

  static final String CDATA_START = "<![CDATA[";
  private static final String CDATA_END = "]]>";

  private final XmlCursor cursor;
  private final XmlPositions positions;

  XmlText(final XmlCursor cursor, final XmlPositions positions) {
    this.cursor = cursor;
    this.positions = positions;
  }

  /** Reads a CDATA section, at {@link #CDATA_START}, as a text node. */
  void cdata(final List<SyntaxNode> children) {
    final int start = cursor.position();
    final int from = start + CDATA_START.length();
    final int end = cursor.text().indexOf(CDATA_END, from);
    if (end < 0) {
      throw new XmlSyntaxException("unterminated CDATA section", start);
    }
    cursor.moveTo(end + CDATA_END.length());
    children.add(node(start, cursor.position(), cursor.text().substring(from, end)));
  }

  /** Reads text up to the next tag; text that is only white space is not kept. */
  void characters(final List<SyntaxNode> children) {
    final int start = cursor.position();
    final int next = cursor.text().indexOf('<', start);
    final int end = next < 0 ? cursor.text().length() : next;
    cursor.moveTo(end);
    final String value = XmlEntities.decode(cursor.text().substring(start, end), start);
    if (!value.isBlank()) {
      children.add(node(start, end, value));
    }
  }

  private SyntaxNode node(final int start, final int end, final String value) {
    return new SyntaxNode(
        AndroidSyntax.TEXT,
        positions.span(start, end),
        Map.of(AndroidSyntax.VALUE, value),
        List.of());
  }
}
