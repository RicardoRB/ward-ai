package dev.ward.analysis.frontend.antlr.format.csharp;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * Builds the {@link Document} of one C# parse, the way {@code dotnet format whitespace} writes
 * code. Every gap between two significant tokens is kept as a break, a space or nothing, never
 * removed, so line breaks that C# reads stay where they are; a block's opening brace moves to its
 * own line when the block spans lines, and an {@code else}, {@code catch} or {@code finally} moves
 * below the closing brace of such a block. Lines are indented by brace depth, plus one level for
 * the body of a statement that is not a block and for the statements of a switch section.
 */
final class CSharpLayout {
  private static final char NEWLINE = '\n';
  private static final String SPACE = " ";
  private static final String DIRECTIVE = "#";

  private final CSharpTreeWalk walk;
  private final int[] codePoints;
  private final List<Token> items;
  private final CSharpBraces braces;
  private final CSharpIndent indent;
  private final List<Document> out = new ArrayList<>();

  /**
   * Creates a layout for one parse.
   *
   * @param parse the parse of the source
   */
  CSharpLayout(final CSharpParse parse) {
    this.walk = parse.walk();
    this.codePoints = parse.codePoints();
    this.items = parse.items();
    this.braces = new CSharpBraces(parse);
    this.indent = new CSharpIndent(items, braces, walk);
  }

  /**
   * Builds the document.
   *
   * @return the document, ending with a line break when it has any token
   */
  Document build() {
    for (int i = 0; i < items.size(); i++) {
      separate(i);
      out.add(Document.text(CSharpTokens.text(codePoints, items.get(i))));
    }
    trailing();
    return Document.concat(out.toArray(Document[]::new));
  }

  private void separate(final int index) {
    final Token token = items.get(index);
    final Token prev = index == 0 ? null : items.get(index - 1);
    final String gap = CSharpTokens.gap(codePoints, prev, token);
    if (prev != null && isVerbatim(prev, token)) {
      out.add(Document.text(gap));
    } else if (prev == null) {
      leading(gap);
    } else if (gap.indexOf(NEWLINE) >= 0 || braces.mustBreak(index)) {
      lines(gap, index);
    } else {
      addSpacing(index, gap);
    }
  }

  /** A gap inside one interpolated string is part of its value. */
  private boolean isVerbatim(final Token prev, final Token next) {
    final int string = walk.stringOf(prev.getTokenIndex());
    return string >= 0 && string == walk.stringOf(next.getTokenIndex());
  }

  /** The directive lines before the first token, each on its own line, and the blank lines
   * after the first of them. */
  private void leading(final String gap) {
    final String[] parts = gap.split("\n", -1);
    boolean seen = false;
    for (int i = 0; i < parts.length - 1; i++) {
      if (parts[i].trim().startsWith(DIRECTIVE)) {
        out.add(Document.text(parts[i].trim()));
        out.add(Document.hardLine());
        seen = true;
      } else if (seen) {
        out.add(Document.hardLine());
      }
    }
  }

  /** The blank lines and directive lines of a gap, then the break before the token. */
  private void lines(final String gap, final int index) {
    final String[] parts = gap.split("\n", -1);
    for (int i = 1; i < parts.length - 1; i++) {
      final String line = parts[i].trim();
      if (line.startsWith(DIRECTIVE)) {
        out.add(CSharpIndent.at(indent.directiveDepth(line, index), Document.hardLine()));
        out.add(Document.text(line));
      } else {
        out.add(Document.hardLine());
      }
    }
    out.add(CSharpIndent.at(indent.depth(index), Document.hardLine()));
  }

  private void addSpacing(final int index, final String gap) {
    final Token prev = items.get(index - 1);
    final Token token = items.get(index);
    final boolean comment = CSharpTokens.isComment(prev) || CSharpTokens.isComment(token);
    final CSharpSpacing.Kind kind =
        comment ? CSharpSpacing.Kind.KEEP : spacing(prev, token).spacing();
    final boolean kept = kind == CSharpSpacing.Kind.KEEP && !gap.isEmpty();
    if (kind == CSharpSpacing.Kind.SPACE || kept || CSharpTokens.isComment(token)) {
      out.add(Document.text(SPACE));
    }
  }

  private CSharpSpacing spacing(final Token prev, final Token token) {
    return new CSharpSpacing(
        CSharpTokens.text(codePoints, prev),
        walk.role(prev.getTokenIndex()),
        CSharpTokens.text(codePoints, token),
        walk.role(token.getTokenIndex())
    );
  }

  /** The directive lines after the last token, then the final line break. */
  private void trailing() {
    if (items.isEmpty()) {
      return;
    }
    final Token last = items.get(items.size() - 1);
    final String[] parts = CSharpTokens.gap(codePoints, last, null).split("\n", -1);
    for (int i = 1; i < parts.length; i++) {
      final String line = parts[i].trim();
      if (line.startsWith(DIRECTIVE)) {
        out.add(CSharpIndent.at(indent.directiveDepth(line, items.size()), Document.hardLine()));
        out.add(Document.text(line));
      }
    }
    out.add(Document.hardLine());
  }
}
