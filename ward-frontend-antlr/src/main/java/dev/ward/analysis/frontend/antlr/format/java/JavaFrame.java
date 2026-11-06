package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayList;
import java.util.List;

/** One bracket scope of the layout: the documents written inside it and the text that closes it. */
final class JavaFrame {

  /** The kinds of scope a frame can be. */
  enum Kind {
    /** The whole file. */
    ROOT,
    /** A block, a class body or a switch body, indented one level. */
    BLOCK,
    /** The statements under one switch label, indented one level. */
    CASE,
    /** An argument, parameter or array-initializer list, broken one item per line. */
    GROUP,
    /** Any other parentheses or brackets, which never break on their own. */
    RAW
  }

  private static final int FIRST_LEVEL = 0;

  private final Kind kind;
  private final String close;
  private final List<Document> parts = new ArrayList<>();

  /**
   * Creates an empty frame.
   *
   * @param kind the scope kind
   * @param close the text that closes the scope
   */
  JavaFrame(final Kind kind, final String close) {
    this.kind = kind;
    this.close = close;
  }

  /**
   * Indents a document by a number of levels.
   *
   * @param body the document to indent
   * @param levels the number of indentation levels
   * @return the indented document
   */
  static Document indented(final Document body, final int levels) {
    Document wrapped = body;
    for (int level = FIRST_LEVEL; level < levels; level++) {
      wrapped = Document.indent(wrapped);
    }
    return wrapped;
  }

  Kind kind() {
    return kind;
  }

  boolean hasClose(final String text) {
    return close.equals(text);
  }

  boolean isEmpty() {
    return parts.isEmpty();
  }

  void add(final Document document) {
    parts.add(document);
  }

  Document body() {
    return new Document.Concat(parts);
  }

  /** Closes a block: its documents go one level deeper, then the close text follows a break. */
  void closeBlock(final JavaFrame parent, final String closeText) {
    if (!parts.isEmpty()) {
      parent.add(Document.indent(body()));
      parent.add(Document.hardLine());
    }
    parent.add(Document.text(closeText));
  }

  /** Closes a group: its items break together, indented by the continuation levels. */
  void closeGroup(
      final JavaFrame parent, final String closeText, final boolean hard, final int levels) {
    if (!parts.isEmpty()) {
      final List<Document> items = new ArrayList<>();
      items.add(Document.softLine());
      items.addAll(parts);
      final Document closing = hard ? Document.hardLine() : Document.softLine();
      final Document inner = indented(new Document.Concat(items), levels);
      parent.add(Document.group(new Document.Concat(List.of(inner, closing))));
    }
    parent.add(Document.text(closeText));
  }

  /** Closes a raw scope: its documents join the parent, and a forced break precedes the close. */
  void closeRaw(final JavaFrame parent, final String closeText, final boolean hard) {
    for (final Document part : parts) {
      parent.add(part);
    }
    if (hard) {
      parent.add(Document.hardLine());
    }
    parent.add(Document.text(closeText));
  }
}
