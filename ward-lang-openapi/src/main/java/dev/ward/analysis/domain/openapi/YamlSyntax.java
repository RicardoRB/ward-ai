package dev.ward.analysis.domain.openapi;

/**
 * The node kinds and field names of the syntax tree that {@link YamlParser} builds for YAML and
 * JSON alike. An {@link #OBJECT} has {@link #ENTRY} children, each with a {@link #KEY} child and a
 * value child; an {@link #ARRAY} has the items as children; a scalar has no children and its text
 * in the {@link #VALUE} field. The tree of a JSON text and of the same document in YAML are equal
 * except for their positions.
 */
public final class YamlSyntax {

  /** A mapping; its children are entries in file order. */
  public static final String OBJECT = "object";

  /** One key and its value: the children are the key node and the value node. */
  public static final String ENTRY = "entry";

  /** The key of an entry; always text, even when written as a number. */
  public static final String KEY = "key";

  /** A sequence; its children are the items. */
  public static final String ARRAY = "array";

  /** A text scalar. */
  public static final String STRING = "string";

  /** A numeric scalar; the value is the text as written. */
  public static final String NUMBER = "number";

  /** A boolean scalar; the value is {@code true} or {@code false}. */
  public static final String BOOLEAN = "boolean";

  /** A null scalar, also the value of a key with nothing after it. */
  public static final String NULL = "null";

  /** The root of a text that could not be read. */
  public static final String DOCUMENT = "document";

  /** The field with the text of a scalar or key. */
  public static final String VALUE = "value";

  /** The field of an expanded alias with the name of the anchor. */
  public static final String ALIAS = "alias";

  /** The field of an expanded alias with the {@code line:column} of the anchored original. */
  public static final String ORIGIN = "origin";

  private YamlSyntax() {}

  /** Tells whether a node kind is a collection, which has children and no text of its own. */
  public static boolean isCollection(final String kind) {
    return OBJECT.equals(kind) || ARRAY.equals(kind);
  }
}
