package dev.ward.analysis.frontend.antlr.format.python;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Sets the blank lines around definitions: two before a top-level {@code def} or {@code class} and
 * after its body, one before a method, none between a decorator and its definition or after a block
 * opener. A blank run inside a body is capped at one line; every other blank run keeps its size,
 * capped by the blank line option.
 */
final class PythonBlankLines {

  private static final int TOP_LEVEL_BLANKS = 2;
  private static final int METHOD_BLANKS = 1;

  private PythonBlankLines() {}

  /**
   * Applies the blank line rules to the chunks.
   *
   * @param chunks the chunks in order
   * @param maxBlank the most blank lines kept in a row
   * @return the chunks with the blank runs set
   */
  static List<PythonChunk> apply(final List<PythonChunk> chunks, final int maxBlank) {
    final List<PythonChunk> out = new ArrayList<>();
    final List<PythonChunk> run = new ArrayList<>();
    PythonChunk previous = null;
    boolean topDef = false;
    for (final PythonChunk chunk : chunks) {
      if (!chunk.isCode()) {
        run.add(chunk);
        continue;
      }
      final OptionalInt target = bodyTarget(run, chunk, target(previous, chunk, topDef, maxBlank));
      out.addAll(target.isPresent() ? withBlanks(run, target.getAsInt()) : run);
      run.clear();
      out.add(chunk);
      topDef = nextTopDef(chunk, topDef);
      previous = chunk;
    }
    out.addAll(withoutTrailingBlanks(run));
    return out;
  }

  /** Caps a blank run inside a body at one line, unless a definition rule already sets it. */
  private static OptionalInt bodyTarget(
      final List<PythonChunk> run, final PythonChunk chunk, final OptionalInt target) {
    if (target.isPresent() || chunk.depth() == 0 || blankCount(run) <= METHOD_BLANKS) {
      return target;
    }
    return OptionalInt.of(METHOD_BLANKS);
  }

  private static int blankCount(final List<PythonChunk> run) {
    return run.stream().filter(PythonChunk::isBlank).mapToInt(PythonChunk::count).sum();
  }

  private static OptionalInt target(
      final PythonChunk previous,
      final PythonChunk chunk,
      final boolean topDef,
      final int maxBlank) {
    if (previous == null) {
      return OptionalInt.of(0);
    }
    if (chunk.depth() == 0) {
      return topLevelTarget(previous, chunk, topDef, maxBlank);
    }
    if (chunk.isDef()) {
      final boolean noBlank = previous.opensBlock() || previous.isDecorator();
      return OptionalInt.of(noBlank ? 0 : Math.min(METHOD_BLANKS, maxBlank));
    }
    return OptionalInt.empty();
  }

  private static OptionalInt topLevelTarget(
      final PythonChunk previous,
      final PythonChunk chunk,
      final boolean topDef,
      final int maxBlank) {
    final int twoBlanks = Math.min(TOP_LEVEL_BLANKS, maxBlank);
    if (chunk.isDef() || chunk.isClass() || chunk.isDecorator()) {
      return OptionalInt.of(previous.isDecorator() ? 0 : twoBlanks);
    }
    return topDef ? OptionalInt.of(twoBlanks) : OptionalInt.empty();
  }

  private static boolean nextTopDef(final PythonChunk chunk, final boolean topDef) {
    if (chunk.depth() != 0 || chunk.isDecorator()) {
      return topDef;
    }
    return chunk.isDef() || chunk.isClass();
  }

  /** Replaces the blank lines before the first comment of a run with the target count. */
  private static List<PythonChunk> withBlanks(final List<PythonChunk> run, final int target) {
    final List<PythonChunk> result = new ArrayList<>();
    if (target > 0) {
      result.add(PythonChunk.blank(target));
    }
    int firstComment = run.size();
    for (int index = 0; index < run.size(); index++) {
      if (!run.get(index).isBlank()) {
        firstComment = index;
        break;
      }
    }
    result.addAll(run.subList(firstComment, run.size()));
    return result;
  }

  private static List<PythonChunk> withoutTrailingBlanks(final List<PythonChunk> run) {
    int end = run.size();
    while (end > 0 && run.get(end - 1).isBlank()) {
      end--;
    }
    return run.subList(0, end);
  }
}
