package dev.ward.analysis.frontend.antlr.typescript;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tells the lexer whether a {@code }} closes a template substitution or is a plain brace. It counts
 * the template literals that are open, and the braces inside the substitution being lexed; a
 * substitution inside another one, such as {@code `${f({ k: `${v}` })}`}, keeps the count of the
 * code around it.
 */
public final class TemplateNesting {

  private int depth;
  private int braces;
  private final Deque<Integer> enclosingBraces = new ArrayDeque<>();

  /** Enters a template literal, at its opening backtick. */
  public void enterLiteral() {
    depth++;
  }

  /** Leaves a template literal, at its closing backtick. */
  public void leaveLiteral() {
    depth--;
  }

  /** Starts a substitution, at {@code ${}, keeping the brace count of the code around it. */
  public void startSubstitution() {
    enclosingBraces.push(braces);
    braces = 0;
  }

  /** Ends a substitution, at its closing brace, and restores the brace count around it. */
  public void endSubstitution() {
    braces = enclosingBraces.isEmpty() ? 0 : enclosingBraces.pop();
  }

  /**
   * Tells whether a closing brace here ends a substitution.
   *
   * @return {@code true} directly inside a substitution, with no brace of its own open
   */
  public boolean atSubstitutionEnd() {
    return depth > 0 && braces == 0;
  }

  /** Counts an opening brace. */
  public void braceOpened() {
    braces++;
  }

  /** Counts a closing brace. */
  public void braceClosed() {
    braces--;
  }

  /** Forgets the state of the previous unit. */
  public void reset() {
    depth = 0;
    braces = 0;
    enclosingBraces.clear();
  }
}
