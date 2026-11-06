package dev.ward.analysis.frontend.antlr.format.kotlin;

import dev.ward.analysis.domain.format.Document;

/** Places a break at an indent depth, for the layout's line breaks. */
final class KotlinBreaks {

  private KotlinBreaks() {}

  /**
   * Wraps a line in one indent per level, so that its break prints at that depth.
   *
   * @param depth the indent depth, in levels
   * @param line the line to place
   * @return the line at that depth
   */
  static Document at(final int depth, final Document line) {
    Document indented = line;
    for (int i = 0; i < depth; i++) {
      indented = Document.indent(indented);
    }
    return indented;
  }
}
