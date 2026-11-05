package dev.ward.analysis.frontend.antlr.format.python;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayList;
import java.util.List;

/**
 * The layout items of one bracket pair, or of the logical line itself (the root frame). A bracket
 * frame may break after its opening bracket and before its closing bracket.
 */
final class PythonFrame {

  private static final String OPENING_PARENTHESIS = "(";
  private static final String COLON = ":";
  private static final String COMMA = ",";

  private final String opener;
  private final boolean subscript;
  private final List<Document> items = new ArrayList<>();
  private boolean annotated;

  private PythonFrame(final String opener, final boolean subscript) {
    this.opener = opener;
    this.subscript = subscript;
  }

  /**
   * Creates the frame of a logical line.
   *
   * @return the root frame
   */
  static PythonFrame root() {
    return new PythonFrame(null, false);
  }

  /**
   * Creates the frame of a bracket.
   *
   * @param opener the opening bracket text
   * @param subscript whether the bracket starts a subscript
   * @return the frame
   */
  static PythonFrame bracket(final String opener, final boolean subscript) {
    return new PythonFrame(opener, subscript);
  }

  boolean isRoot() {
    return opener == null;
  }

  boolean isParenthesis() {
    return OPENING_PARENTHESIS.equals(opener);
  }

  String opener() {
    return opener;
  }

  boolean subscript() {
    return subscript;
  }

  /**
   * Tells whether the current argument of a parenthesis has an annotation, so that its default
   * value takes spaces around the equals sign.
   *
   * @return true after a colon in the current argument
   */
  boolean annotated() {
    return annotated;
  }

  List<Document> items() {
    return items;
  }

  /**
   * Records a token for the annotation state of the current argument.
   *
   * @param text the token text
   */
  void mark(final String text) {
    if (COMMA.equals(text)) {
      annotated = false;
    } else if (COLON.equals(text) && isParenthesis()) {
      annotated = true;
    }
  }
}
