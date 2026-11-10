package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.frontend.antlr.java.JavaLexer;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * One code token or comment, with the whitespace that came before it in the source.
 *
 * @param index the token index in the token stream
 * @param text the token text exactly as written
 * @param type the lexer token type
 * @param newlines the line breaks in the whitespace before the token
 * @param gapSpace whether any whitespace came before the token
 */
record JavaAtom(int index, String text, int type, int newlines, boolean gapSpace) {

  private static final char NEWLINE = '\n';

  /**
   * Turns a token stream into atoms, dropping the end-of-file token and folding whitespace into the
   * gap before the next atom.
   *
   * @param tokens every token of the source, hidden channels included
   * @return the atoms in source order
   */
  static List<JavaAtom> of(final List<Token> tokens) {
    final List<JavaAtom> atoms = new ArrayList<>();
    final StringBuilder gap = new StringBuilder();
    for (final Token token : tokens) {
      final int type = token.getType();
      if (type == Token.EOF) {
        break;
      }
      if (type == JavaLexer.WS) {
        gap.append(token.getText());
      } else {
        atoms.add(
            new JavaAtom(
                token.getTokenIndex(), token.getText(), type, countNewlines(gap), !gap.isEmpty()));
        gap.setLength(0);
      }
    }
    return List.copyOf(atoms);
  }

  /**
   * Tells whether this atom is a comment.
   *
   * @return true for a block or a line comment
   */
  boolean comment() {
    return type == JavaLexer.COMMENT || type == JavaLexer.LINE_COMMENT;
  }

  /**
   * Tells whether this atom is a line comment, which must end its line.
   *
   * @return true for a line comment
   */
  boolean lineComment() {
    return type == JavaLexer.LINE_COMMENT;
  }

  private static int countNewlines(final CharSequence gap) {
    int count = 0;
    for (int i = 0; i < gap.length(); i++) {
      if (gap.charAt(i) == NEWLINE) {
        count++;
      }
    }
    return count;
  }
}
