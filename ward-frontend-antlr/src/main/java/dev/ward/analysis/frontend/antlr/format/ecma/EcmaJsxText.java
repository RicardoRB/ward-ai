package dev.ward.analysis.frontend.antlr.format.ecma;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Collects the source of one JSX element, from its opening {@code <} to the end that matches it.
 */
final class EcmaJsxText {

  /** The token that opens an element. */
  static final String OPEN = "JsxOpenLt";

  private static final Set<String> ENDS = Set.of("JsxSelfCloseEnd", "JsxClosingTagEnd");

  private final List<String> parts = new ArrayList<>();
  private int depth;

  /**
   * Tells whether an element is being collected.
   *
   * @return {@code true} between an opening {@code <} and its matching end
   */
  boolean active() {
    return depth > 0;
  }

  /**
   * Adds one token of the element.
   *
   * @param name the symbolic name of the token
   * @param text the exact source of the token
   * @return {@code true} when the element has ended with this token
   */
  boolean add(final String name, final String text) {
    parts.add(text);
    if (OPEN.equals(name)) {
      depth++;
    } else if (ENDS.contains(name)) {
      depth--;
    }
    return depth == 0;
  }

  /**
   * Returns the source collected so far, and starts the next element empty.
   *
   * @return the exact source of the element
   */
  String take() {
    final String text = String.join("", parts);
    parts.clear();
    return text;
  }
}
