package dev.ward.analysis.frontend.antlr.format.csharp;

import dev.ward.analysis.domain.format.Document;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The indent depth of a line of C#: the braces open before it, plus the levels that the tree adds
 * for the body of a statement and for a switch section, and one less for a closing brace.
 */
final class CSharpIndent {
  private static final List<String> INDENTED_DIRECTIVES = List.of("#region", "#endregion");

  private final List<Token> items;
  private final CSharpBraces braces;
  private final CSharpTreeWalk walk;

  /**
   * Creates the indent rules for a parse.
   *
   * @param items the significant tokens and comments
   * @param braces the braces of the parse
   * @param walk the tree facts of the parse
   */
  CSharpIndent(final List<Token> items, final CSharpBraces braces, final CSharpTreeWalk walk) {
    this.items = items;
    this.braces = braces;
    this.walk = walk;
  }

  /**
   * Returns the depth of the line that starts with an item.
   *
   * @param index the item's index
   * @return the depth, in levels
   */
  int depth(final int index) {
    return contentDepth(index) - (braces.isBrace(index, "}") ? 1 : 0);
  }

  /**
   * Returns the depth of a directive line that comes before an item. {@code #region} and {@code
   * #endregion} stand where code stands; other directives start in the first column.
   *
   * @param line the directive line
   * @param index the index of the item after it, or the item count at the end of the source
   * @return the depth, in levels
   */
  int directiveDepth(final String line, final int index) {
    final boolean indented = INDENTED_DIRECTIVES.stream().anyMatch(line::startsWith);
    return indented ? contentDepth(index) : 0;
  }

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

  /** The depth of code that stands at this place, before any closing brace of the item. */
  private int contentDepth(final int index) {
    final int extra = index < items.size() ? walk.extraLevels(items.get(index).getTokenIndex()) : 0;
    return braces.openBefore(index) + extra;
  }
}
