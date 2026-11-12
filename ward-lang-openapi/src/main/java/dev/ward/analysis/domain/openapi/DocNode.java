package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable node of a parsed OpenAPI document, in the order of its file. It knows the file it
 * came from, its JSON pointer inside that file ({@code /paths/~1pets/get}) and where it is written,
 * so a rule or a diff always has the position of what it read. Object keys keep their order and a
 * duplicate key never gets this far: the parser rejects it.
 *
 * <p>Two nodes are equal only when they are the same node: a node is a place in a file, and two
 * places with the same content are not the same place.
 *
 * @param kind what the node holds
 * @param text the text of a scalar, or the empty text for an object and an array
 * @param file the project-relative path of the file the node is written in
 * @param pointer the JSON pointer of the node inside its file; the root is the empty text
 * @param span where the value is written
 * @param keySpan where the key of this node is written; empty for a root and an array item
 * @param entries the entries of an object in file order; empty for any other node
 * @param items the items of an array in file order; empty for any other node
 */
public record DocNode(
    Kind kind,
    String text,
    String file,
    String pointer,
    Span span,
    Optional<Span> keySpan,
    List<Entry> entries,
    List<DocNode> items) {

  /**
   * Copies the entries and items.
   *
   * @throws NullPointerException if a part is missing
   */
  public DocNode {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(pointer, "pointer");
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(keySpan, "keySpan");
    entries = List.copyOf(entries);
    items = List.copyOf(items);
  }

  /** Returns the value of {@code key} in an object, or empty. */
  public Optional<DocNode> get(final String key) {
    for (final Entry entry : entries) {
      if (entry.key().equals(key)) {
        return Optional.of(entry.value());
      }
    }
    return Optional.empty();
  }

  /** Returns the text of the scalar string at {@code key} of an object, or empty. */
  public Optional<String> string(final String key) {
    return get(key).filter(child -> child.kind == Kind.STRING).map(DocNode::text);
  }

  /** Returns the item at {@code index} of an array, or empty. */
  public Optional<DocNode> at(final int index) {
    return index >= 0 && index < items.size() ? Optional.of(items.get(index)) : Optional.empty();
  }

  /** Returns the value nodes of an object or the items of an array, in file order. */
  public List<DocNode> children() {
    return kind == Kind.OBJECT ? entries.stream().map(Entry::value).toList() : items;
  }

  /** Returns where a finding about this node points: its key when it has one, else its value. */
  public Span anchor() {
    return keySpan.orElse(span);
  }

  /** Tells whether the node is a scalar. */
  public boolean isScalar() {
    return kind != Kind.OBJECT && kind != Kind.ARRAY;
  }

  /** Returns the target text of a {@code $ref} object, or empty for any other node. */
  public Optional<String> reference() {
    return string(OpenApiDocument.REF);
  }

  @Override
  public boolean equals(final Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  /** What a node holds. */
  public enum Kind {
    /** A mapping; read it with {@link #entries()} and {@link #get(String)}. */
    OBJECT,
    /** A sequence; read it with {@link #items()}. */
    ARRAY,
    /** A text scalar. */
    STRING,
    /** A number; {@link #text()} is as written. */
    NUMBER,
    /** A boolean; {@link #text()} is {@code true} or {@code false}. */
    BOOLEAN,
    /** A null, also a key with nothing after it. */
    NULL
  }

  /**
   * One key and value of an object.
   *
   * @param key the key text
   * @param keySpan where the key is written
   * @param value the value node
   * @param numericKey whether the key was written without quotes and YAML reads it as a number
   */
  public record Entry(String key, Span keySpan, DocNode value, boolean numericKey) {

    /**
     * Validates the parts.
     *
     * @throws NullPointerException if a part is missing
     */
    public Entry {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(keySpan, "keySpan");
      Objects.requireNonNull(value, "value");
    }

    /** An entry whose key is text, as every JSON key and every quoted YAML key is. */
    public Entry(final String key, final Span keySpan, final DocNode value) {
      this(key, keySpan, value, false);
    }
  }
}
