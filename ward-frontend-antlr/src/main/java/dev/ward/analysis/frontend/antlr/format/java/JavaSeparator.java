package dev.ward.analysis.frontend.antlr.format.java;

/**
 * Holds the break the previous token left and decides the break before the next token: the explicit
 * rule of the previous token first, then the next token's own rule, then the source whitespace.
 */
final class JavaSeparator {

  private final JavaSpacing spacing;
  private JavaBreak pending = JavaBreak.UNSET;
  private boolean afterBlockClose;
  private boolean afterAnnotation;
  private JavaAtom previous;

  /**
   * Creates the separator for one source.
   *
   * @param spacing the spacing rules for tokens on one line
   */
  JavaSeparator(final JavaSpacing spacing) {
    this.spacing = spacing;
  }

  /**
   * Remembers the atom just written, so the next token's spacing can look back at it.
   *
   * @param atom the atom just written
   */
  void remember(final JavaAtom atom) {
    previous = atom;
  }

  /**
   * Decides the break before a code token.
   *
   * @param atom the next token
   * @param blockTop whether the enclosing scope is a block
   * @param hasOutput whether anything was written before the token
   * @return the break to write
   */
  JavaBreak resolve(final JavaAtom atom, final boolean blockTop, final boolean hasOutput) {
    final JavaBreak explicit = explicitBreak(atom, blockTop);
    return explicit == JavaBreak.UNSET ? gapBreak(atom, blockTop, hasOutput) : explicit;
  }

  /**
   * Returns the pending break and clears it.
   *
   * @return the break that was pending
   */
  JavaBreak takePending() {
    final JavaBreak taken = pending;
    pending = JavaBreak.UNSET;
    return taken;
  }

  /**
   * Records what a code token leaves for the next one.
   *
   * @param next the break after the token
   * @param blockClosed whether the token closed a block
   * @param annotationEnd whether the token ends an annotation
   */
  void endCode(final JavaBreak next, final boolean blockClosed, final boolean annotationEnd) {
    pending = next;
    afterBlockClose = blockClosed;
    afterAnnotation = annotationEnd;
  }

  /**
   * Records a comment. A line comment always leaves a break after it.
   *
   * @param lineComment whether the comment is a line comment
   */
  void endComment(final boolean lineComment) {
    afterBlockClose = false;
    afterAnnotation = false;
    if (lineComment) {
      pending = JavaBreak.HARD;
    }
  }

  void setPending(final JavaBreak next) {
    pending = next;
  }

  private JavaBreak explicitBreak(final JavaAtom atom, final boolean blockTop) {
    if (afterBlockClose) {
      return JavaBreakRules.afterBlock(atom.text());
    }
    if (afterAnnotation) {
      return atom.newlines() > 0 && blockTop ? JavaBreak.HARD : JavaBreak.SPACE;
    }
    if (pending == JavaBreak.HARD || pending == JavaBreak.LINE || pending == JavaBreak.NONE) {
      return pending;
    }
    return JavaBreak.UNSET;
  }

  private JavaBreak gapBreak(final JavaAtom atom, final boolean blockTop, final boolean hasOutput) {
    final JavaBreak before = JavaBreakRules.beforeOf(atom, blockTop);
    if (before != JavaBreak.UNSET) {
      return before;
    }
    if (pending == JavaBreak.SPACE) {
      return JavaBreak.SPACE;
    }
    if (atom.newlines() > 0 && blockTop && hasOutput) {
      return JavaBreak.CONTINUE;
    }
    return previous != null && spacing.spaceBetween(previous, atom)
        ? JavaBreak.SPACE
        : JavaBreak.NONE;
  }
}
