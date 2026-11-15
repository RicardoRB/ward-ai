package dev.ward.analysis.domain.android;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the attributes of a start tag. */
final class XmlAttributes {

  private static final String SELF_CLOSE = "/>";

  private final XmlCursor cursor;
  private final XmlPositions positions;

  XmlAttributes(final XmlCursor cursor, final XmlPositions positions) {
    this.cursor = cursor;
    this.positions = positions;
  }

  /** Reads the attributes up to the {@code >} or {@code />} that ends the start tag. */
  List<SyntaxNode> read() {
    final List<SyntaxNode> attributes = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    XmlTokens.skipSpace(cursor);
    while (!cursor.at('>') && !cursor.startsWith(SELF_CLOSE)) {
      if (cursor.atEnd()) {
        throw cursor.problem("unterminated start tag");
      }
      final int start = cursor.position();
      final SyntaxNode attribute = attribute();
      if (!seen.add(attribute.fields().get(AndroidSyntax.NAME))) {
        throw new XmlSyntaxException("duplicate attribute", start);
      }
      attributes.add(attribute);
      XmlTokens.skipSpace(cursor);
    }
    return attributes;
  }

  private SyntaxNode attribute() {
    final int start = cursor.position();
    final String name = XmlTokens.name(cursor);
    XmlTokens.skipSpace(cursor);
    XmlTokens.expect(cursor, '=');
    XmlTokens.skipSpace(cursor);
    final char quote = quote();
    cursor.advance(1);
    final int valueStart = cursor.position();
    final int valueEnd = cursor.text().indexOf(quote, valueStart);
    if (valueEnd < 0) {
      throw new XmlSyntaxException("unterminated attribute value", valueStart);
    }
    final String value =
        XmlEntities.decode(cursor.text().substring(valueStart, valueEnd), valueStart);
    cursor.moveTo(valueEnd + 1);
    final int colon = name.indexOf(':');
    return new SyntaxNode(
        AndroidSyntax.ATTRIBUTE,
        positions.span(start, cursor.position()),
        Map.of(
            AndroidSyntax.NAME,
            name,
            AndroidSyntax.PREFIX,
            colon < 0 ? "" : name.substring(0, colon),
            AndroidSyntax.LOCAL,
            name.substring(colon + 1),
            AndroidSyntax.VALUE,
            value),
        List.of());
  }

  private char quote() {
    if (cursor.at('"')) {
      return '"';
    }
    if (cursor.at('\'')) {
      return '\'';
    }
    throw cursor.problem("expected a quoted attribute value");
  }
}
