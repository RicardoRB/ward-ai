package dev.ward.analysis.domain.ir;

/**
 * A region of a source unit.
 *
 * <p>Lines and columns are 1-based, like findings. Offsets are character indexes into the source
 * text, so caches can compare regions without re-parsing lines.
 *
 * @param startLine first line, 1-based
 * @param startColumn first column, 1-based
 * @param endLine last line, 1-based
 * @param endColumn last column, 1-based
 * @param startOffset first character offset, zero-based
 * @param endOffset character offset after the last character
 */
public record Span(
    int startLine, int startColumn, int endLine, int endColumn, int startOffset, int endOffset) {

  /**
   * Validates the positions.
   *
   * @throws IllegalArgumentException if a line or column is not positive, an offset is negative, or
   *     the end precedes the start
   */
  public Span {
    if (startLine < 1 || startColumn < 1 || endLine < 1 || endColumn < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
    if (startOffset < 0 || endOffset < startOffset) {
      throw new IllegalArgumentException("end offset precedes start offset");
    }
    if (endLine < startLine || endLine == startLine && endColumn < startColumn) {
      throw new IllegalArgumentException("end precedes start");
    }
  }
}
