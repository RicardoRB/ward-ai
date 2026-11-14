package dev.ward.analysis.domain.android;

/**
 * The node kinds and field names of the syntax tree that {@link XmlParser} builds. The root is the
 * {@link #ELEMENT} of the document, or a {@link #DOCUMENT} with no children when the text could not
 * be read. An element's children are its {@link #ATTRIBUTE} nodes first, then its child elements
 * and {@link #TEXT} nodes in source order.
 */
public final class AndroidSyntax {

  /** An XML element; the {@link #NAME} field is its name as written, such as {@code service}. */
  public static final String ELEMENT = "element";

  /** An attribute; its span covers {@code name="value"}. */
  public static final String ATTRIBUTE = "attribute";

  /** Text or CDATA between elements; blank text is not kept. */
  public static final String TEXT = "text";

  /** The root of a text that could not be read. */
  public static final String DOCUMENT = "document";

  /** The field with the name of an element or attribute, as written. */
  public static final String NAME = "name";

  /** The field of an attribute with the name after its prefix, such as {@code exported}. */
  public static final String LOCAL = "local";

  /** The field of an attribute with its prefix, such as {@code android}; empty without one. */
  public static final String PREFIX = "prefix";

  /** The field with the decoded value of an attribute or text. */
  public static final String VALUE = "value";

  private AndroidSyntax() {}

  /** Tells whether a node kind is an element. */
  public static boolean isElement(final String kind) {
    return ELEMENT.equals(kind);
  }
}
