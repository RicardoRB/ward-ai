package dev.ward.analysis.frontend.antlr.format.python;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the entries of a source into chunks: blank runs capped by the blank line option, own-line
 * comments and logical lines. Indentation comes from the lexer's INDENT and DEDENT entries, so the
 * nesting of the source never changes; an own-line comment takes the level of the block whose
 * column it is at.
 */
final class PythonChunker {

  private final String source;
  private final String[] lines;
  private final String indentUnit;
  private final int maxBlank;
  private final List<PythonChunk> chunks = new ArrayList<>();
  private final List<Integer> columns = new ArrayList<>();
  private final List<Pending> pending = new ArrayList<>();
  private PythonLine line;
  private boolean lineOpen;
  private boolean indentPending;
  private int lastEnd;

  private static final int ROOT_COLUMNS = 1;

  /** An own-line comment whose level waits for the next line of code. */
  private record Pending(int index, int column) {}

  /**
   * Creates a chunker for one source text.
   *
   * @param source the source text, with {@code \n} line endings
   * @param indentUnit the text of one indentation level
   * @param maxBlank the most blank lines kept in a row
   */
  PythonChunker(final String source, final String indentUnit, final int maxBlank) {
    this.source = source;
    this.lines = source.split("\n", -1);
    this.indentUnit = indentUnit;
    this.maxBlank = maxBlank;
    columns.add(0);
  }

  /**
   * Chunks the entries.
   *
   * @param entries the entries in source order
   * @return the chunks in order
   */
  List<PythonChunk> run(final List<PythonEntry> entries) {
    for (final PythonEntry entry : entries) {
      dispatch(entry);
    }
    if (lineOpen) {
      finishLine();
    }
    resolvePending();
    return chunks;
  }

  private void dispatch(final PythonEntry entry) {
    if (entry.kind() == PythonEntry.Kind.NEWLINE) {
      if (lineOpen) {
        finishLine();
      }
    } else if (entry.kind() == PythonEntry.Kind.INDENT) {
      indentPending = true;
    } else if (entry.kind() == PythonEntry.Kind.DEDENT) {
      if (columns.size() > ROOT_COLUMNS) {
        columns.removeLast();
      }
    } else if (entry.kind() == PythonEntry.Kind.COMMENT) {
      comment(entry);
    } else {
      token(entry);
    }
  }

  private void comment(final PythonEntry entry) {
    if (lineOpen && (!entry.ownLine() || line.insideBrackets())) {
      line.addComment(entry);
    } else {
      if (lineOpen) {
        finishLine();
      }
      addBlank(entry.line());
      pending.add(new Pending(chunks.size(), entry.column()));
      chunks.add(PythonChunk.comment(0, entry.text()));
    }
    lastEnd = entry.line();
  }

  /** Gives the own-line comments waiting for their block the level of their column. */
  private void resolvePending() {
    for (final Pending waiting : pending) {
      final int depth = depthOf(waiting.column());
      final String text = chunks.get(waiting.index()).text();
      chunks.set(waiting.index(), PythonChunk.comment(depth, indentUnit.repeat(depth) + text));
    }
    pending.clear();
  }

  private void token(final PythonEntry entry) {
    if (!lineOpen) {
      addBlank(entry.line());
      if (indentPending) {
        columns.add(entry.column());
        indentPending = false;
      }
      resolvePending();
      line = new PythonLine(source, columns.size() - 1, indentUnit);
      lineOpen = true;
    }
    line.addToken(entry);
    lastEnd = entry.endLine();
  }

  private void finishLine() {
    chunks.add(line.toChunk());
    lineOpen = false;
  }

  private void addBlank(final int lineNumber) {
    if (chunks.isEmpty()) {
      return;
    }
    final int kept = Math.min(blankBetween(lastEnd, lineNumber), maxBlank);
    if (kept > 0) {
      chunks.add(PythonChunk.blank(kept));
    }
  }

  /** Counts the blank lines strictly between two lines. */
  private int blankBetween(final int from, final int to) {
    int count = 0;
    for (int number = from + 1; number < to; number++) {
      if (lines[number - 1].isBlank()) {
        count++;
      }
    }
    return count;
  }

  /** The level of the innermost block whose column is at or before the given column. */
  private int depthOf(final int column) {
    int depth = 0;
    for (int level = 0; level < columns.size(); level++) {
      if (columns.get(level) <= column) {
        depth = level;
      }
    }
    return depth;
  }
}
