package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.domain.format.Document;
import java.util.List;

/**
 * Builds the layout document of one Java source from its atoms. Blocks indent one level, argument
 * and parameter lists break one item per line, a statement ends its line, and continuation lines
 * are indented by the continuation levels. Only whitespace is decided here: every token is written
 * as it was.
 */
final class JavaDocBuilder {

  private static final String SPACE = " ";
  private static final String EMPTY = "";
  private static final String BLANK_LINE = "\n";
  private static final int NO_BLANK_LINE = 1;

  private final List<JavaAtom> atoms;
  private final JavaTreeFacts facts;
  private final int maxBlankLines;
  private final int continuationLevels;
  private final JavaFrameStack stack = new JavaFrameStack();
  private final JavaSeparator separator;
  private boolean hasOutput;
  private JavaAtom previous;

  /**
   * Creates the builder for one source.
   *
   * @param atoms the atoms in source order
   * @param facts the parse-tree facts of the same source
   * @param maxBlankLines the most blank lines kept in a row
   * @param continuationLevels the indentation levels of a continuation line
   */
  JavaDocBuilder(
      final List<JavaAtom> atoms,
      final JavaTreeFacts facts,
      final int maxBlankLines,
      final int continuationLevels) {
    this.atoms = atoms;
    this.facts = facts;
    this.maxBlankLines = maxBlankLines;
    this.continuationLevels = continuationLevels;
    this.separator = new JavaSeparator(new JavaSpacing(atoms, facts));
  }

  /**
   * Builds the document.
   *
   * @return the document, ending in one line break
   * @throws IllegalStateException if the brackets do not balance
   */
  Document build() {
    for (final JavaAtom atom : atoms) {
      if (atom.comment()) {
        emitComment(atom);
      } else {
        emitCode(atom);
      }
      hasOutput = true;
      previous = atom;
      separator.remember(atom);
    }
    if (!stack.isRootOnly()) {
      throw new IllegalStateException("unclosed brackets in Java source");
    }
    if (hasOutput) {
      stack.root().add(Document.hardLine());
    }
    return stack.root().body();
  }

  private void emitComment(final JavaAtom atom) {
    if (previous != null && atom.newlines() == 0) {
      stack.current().add(Document.text((atom.gapSpace() ? SPACE : EMPTY) + atom.text()));
      separator.endComment(atom.lineComment());
      return;
    }
    flush(JavaBreak.HARD, atom);
    stack.current().add(Document.text(atom.text()));
    separator.endComment(true);
  }

  private void emitCode(final JavaAtom atom) {
    if (JavaBreakRules.isClose(atom.text())) {
      closeFrame(atom);
    } else {
      emitOpenOrPlain(atom);
    }
  }

  private void emitOpenOrPlain(final JavaAtom atom) {
    stack.endCaseLabels(atom.text());
    if (startsCaseBody(atom)) {
      stack.popCase();
      separator.setPending(JavaBreak.SPACE);
    }
    flush(separator.resolve(atom, stack.blockTop(), hasOutput), atom);
    if (JavaBreakRules.isOpen(atom.text())) {
      openFrame(atom);
      return;
    }
    stack.current().add(Document.text(atom.text()));
    final boolean groupTop = stack.current().kind() == JavaFrame.Kind.GROUP;
    separator.endCode(
        JavaBreakRules.afterToken(atom, facts, stack.blockTop(), groupTop),
        false,
        facts.annotationEnds().contains(atom.index()));
    if (JavaBreakRules.COLON.equals(atom.text()) && facts.hardAfter().contains(atom.index())) {
      stack.push(JavaFrame.Kind.CASE, EMPTY);
    }
  }

  /** Tells whether a block opens right after a case colon, which keeps the brace on that line. */
  private boolean startsCaseBody(final JavaAtom atom) {
    return JavaBreakRules.OPEN_BLOCK.equals(atom.text())
        && previous != null
        && facts.hardAfter().contains(previous.index())
        && stack.current().kind() == JavaFrame.Kind.CASE;
  }

  private void openFrame(final JavaAtom atom) {
    final JavaFrame.Kind kind = JavaBreakRules.openKind(atom, facts);
    if (kind == JavaFrame.Kind.BLOCK && !facts.expressionBlocks().contains(atom.index())) {
      stack.current().continuations().end();
    }
    stack.current().add(Document.text(atom.text()));
    stack.push(kind, JavaBreakRules.closingOf(atom.text()));
    separator.endCode(kind == JavaFrame.Kind.BLOCK ? JavaBreak.HARD : JavaBreak.NONE, false, false);
  }

  private void closeFrame(final JavaAtom atom) {
    stack.endCases();
    final boolean hard = separator.takePending() == JavaBreak.HARD;
    final JavaFrame frame = stack.pop();
    if (!frame.hasClose(atom.text())) {
      throw new IllegalStateException("unbalanced brackets in Java source");
    }
    final JavaFrame parent = stack.current();
    switch (frame.kind()) {
      case BLOCK -> frame.closeBlock(parent, atom.text());
      case GROUP -> frame.closeGroup(parent, atom.text(), hard, continuationLevels);
      default -> frame.closeRaw(parent, atom.text(), hard);
    }
    separator.endCode(
        JavaBreak.UNSET,
        frame.kind() == JavaFrame.Kind.BLOCK,
        facts.annotationEnds().contains(atom.index()));
  }

  /** Writes the break decided for the next token, or nothing before the first output. */
  private void flush(final JavaBreak taken, final JavaAtom next) {
    if (!hasOutput) {
      return;
    }
    switch (taken) {
      case HARD -> hardBreak(next);
      case CONTINUE ->
          stack
              .current()
              .continuations()
              .continueLine(continuationLevels, JavaBreakRules.lineKind(next.text()));
      case LINE -> {
        stack.current().continuations().end();
        stack.current().add(Document.line());
      }
      case SPACE -> stack.current().add(Document.text(SPACE));
      default -> {
        // NONE and UNSET write nothing.
      }
    }
  }

  /**
   * Writes a line break that always happens. A comment inside a continued statement, and the code
   * after it, stay on the statement's continuation; any other break ends the continuation. At the
   * start of a list the list's own line break already precedes the first item, so nothing is added.
   */
  private void hardBreak(final JavaAtom next) {
    final JavaFrame frame = stack.current();
    if (frame.kind() == JavaFrame.Kind.GROUP && frame.isEmpty()) {
      return;
    }
    final boolean insideStatement =
        previous.comment() || next.comment() && !JavaBreakRules.endsStatement(previous.text());
    if (frame.continuations().active() && insideStatement) {
      frame.continuations().continueLine(continuationLevels, JavaBreakRules.lineKind(next.text()));
      return;
    }
    frame.continuations().end();
    blankLines(next);
    frame.add(Document.hardLine());
  }

  private void blankLines(final JavaAtom next) {
    if (next == null || previous == null || JavaBreakRules.OPEN_BLOCK.equals(previous.text())) {
      return;
    }
    final int blanks = Math.min(next.newlines() - NO_BLANK_LINE, maxBlankLines);
    for (int i = 0; i < blanks; i++) {
      stack.current().add(Document.text(BLANK_LINE));
    }
  }
}
