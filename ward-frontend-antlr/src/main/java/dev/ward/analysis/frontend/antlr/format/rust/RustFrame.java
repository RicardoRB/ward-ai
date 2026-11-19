package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayList;
import java.util.List;

/** One level of nesting while the document is built: its parts and how it indents them. */
final class RustFrame {
  /** Marks a frame that no bracket closes. */
  static final int NO_CLOSE = -1;

  /** What a frame stands for. */
  enum Kind {
    /** The whole file. */
    ROOT,
    /** Brackets that add no indent: they open and close on one line, or hug a later opener. */
    PLAIN,
    /** Brackets that indent their lines. */
    INDENT,
    /** Brackets that indent their lines and whose first item starts on the opening line. */
    HUGGING,
    /** The rest of a statement that continues on a new line, such as a method chain. */
    CONTINUATION
  }

  /** What started a continuation: it decides whether a later break indents once more. */
  enum Origin {
    /** A {@code where} clause: it lasts until the item's body. */
    WHERE,
    /** The line after {@code =} or {@code =>}. */
    ASSIGNMENT,
    /** A line that starts with a method call or field access. */
    DOT,
    /** Any other line of a continued statement, or a bracket. */
    OTHER
  }

  private final Kind kind;
  private final int closeIndex;
  private final Origin origin;
  private final List<Document> parts = new ArrayList<>();

  private RustFrame(final Kind kind, final int closeIndex, final Origin origin) {
    this.kind = kind;
    this.closeIndex = closeIndex;
    this.origin = origin;
  }

  /**
   * Creates a frame closed by the bracket at a position.
   *
   * @param kind the frame kind
   * @param closeIndex the position of the closing bracket, or {@link #NO_CLOSE}
   * @return the frame
   */
  static RustFrame of(final Kind kind, final int closeIndex) {
    return new RustFrame(kind, closeIndex, Origin.OTHER);
  }

  /**
   * Creates a continuation frame.
   *
   * @param origin what started the continuation
   * @return the frame
   */
  static RustFrame continuation(final Origin origin) {
    return new RustFrame(Kind.CONTINUATION, NO_CLOSE, origin);
  }

  Kind getKind() {
    return kind;
  }

  int getCloseIndex() {
    return closeIndex;
  }

  Origin getOrigin() {
    return origin;
  }

  void add(final Document document) {
    parts.add(document);
  }

  /**
   * The document of the frame.
   *
   * @return its parts, indented when the frame indents
   */
  Document document() {
    final Document body = new Document.Concat(parts);
    return kind == Kind.PLAIN || kind == Kind.ROOT ? body : Document.indent(body);
  }
}
