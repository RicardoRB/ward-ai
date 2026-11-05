package dev.ward.analysis.domain.format;

import java.util.List;
import java.util.Objects;

/**
 * A layout document: text, the places where a line may break, and the groups that decide whether
 * those breaks are taken. A language formatter builds one of these from its parse tree; {@link
 * DocPrinter} lays it out for a line width and indentation style.
 */
public sealed interface Document
    permits Document.Text, Document.Line, Document.Indent, Document.Group, Document.Concat {

  /**
   * Verbatim text. It must not carry a line break except inside a token that the source already had
   * on several lines.
   *
   * @param text the text
   */
  record Text(String text) implements Document {

    /**
     * Checks the text.
     *
     * @throws NullPointerException if the text is missing
     */
    public Text {
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * A break point. Flat it prints {@code flat}; broken it starts a new line, unless it is forced.
   *
   * @param flat the text printed when the enclosing group stays on one line
   * @param forced whether the line always breaks, which also forces every enclosing group to break
   */
  record Line(String flat, boolean forced) implements Document {

    /**
     * Checks the flat text.
     *
     * @throws NullPointerException if the flat text is missing
     */
    public Line {
      Objects.requireNonNull(flat, "flat");
    }
  }

  /**
   * Indents every line break inside the body by one level.
   *
   * @param body the indented document
   */
  record Indent(Document body) implements Document {

    /**
     * Checks the body.
     *
     * @throws NullPointerException if the body is missing
     */
    public Indent {
      Objects.requireNonNull(body, "body");
    }
  }

  /**
   * Prints its body flat when the body fits on the rest of the line, and breaks its lines
   * otherwise.
   *
   * @param body the grouped document
   */
  record Group(Document body) implements Document {

    /**
     * Checks the body.
     *
     * @throws NullPointerException if the body is missing
     */
    public Group {
      Objects.requireNonNull(body, "body");
    }
  }

  /**
   * Prints its parts one after another.
   *
   * @param parts the parts, copied on construction
   */
  record Concat(List<Document> parts) implements Document {

    /**
     * Copies the parts.
     *
     * @throws NullPointerException if the list or one of its parts is missing
     */
    public Concat {
      parts = List.copyOf(parts);
    }
  }

  /**
   * Makes verbatim text.
   *
   * @param text the text
   * @return the document
   */
  static Document text(final String text) {
    return new Text(text);
  }

  /**
   * Makes a line that prints a space when flat.
   *
   * @return the document
   */
  static Document line() {
    return new Line(" ", false);
  }

  /**
   * Makes a line that prints nothing when flat.
   *
   * @return the document
   */
  static Document softLine() {
    return new Line("", false);
  }

  /**
   * Makes a line that always breaks.
   *
   * @return the document
   */
  static Document hardLine() {
    return new Line("", true);
  }

  /**
   * Indents the body.
   *
   * @param body the document to indent
   * @return the document
   */
  static Document indent(final Document body) {
    return new Indent(body);
  }

  /**
   * Groups the body.
   *
   * @param body the document to group
   * @return the document
   */
  static Document group(final Document body) {
    return new Group(body);
  }

  /**
   * Concatenates documents.
   *
   * @param parts the documents, in order
   * @return the document
   */
  static Document concat(final Document... parts) {
    return new Concat(List.of(parts));
  }
}
