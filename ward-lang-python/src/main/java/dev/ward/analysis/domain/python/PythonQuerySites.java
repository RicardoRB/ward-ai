package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the value parts of a string that come after a literal {@code ?}: the operands of a
 * concatenation after a string literal with a question mark, and the replacement fields of an
 * f-string after one. Whatever such a part holds can only be part of a URL's query, so a rule about
 * where a URL leads can read it as harmless, and a rule about paths cannot.
 *
 * <p>shortcut: only {@code +} concatenation and f-strings; {@code %} and {@code .format} are not
 * followed, upgrade when a project builds a query with them.
 */
final class PythonQuerySites {
  /** The name of the site: the character that starts the query. */
  static final String QUERY = "?";

  private static final String EXPR = "expr";
  private static final String PLUS = "+";
  private static final char MARK = '?';
  private static final char OPEN = '{';
  private static final char CLOSE = '}';
  private static final Pattern PLAIN_STRING = Pattern.compile(
      "^[rRbBuU]*(['\"]).*",
      Pattern.DOTALL
  );

  private PythonQuerySites() {}

  /**
   * The operands of a concatenation {@code a + b + c}, which the grammar nests to the left, or empty
   * for another node; a caller walks them instead of the nested nodes, so each sum is seen once.
   */
  static List<SyntaxNode> operandsOf(final SyntaxNode node) {
    final List<SyntaxNode> operands = new ArrayList<>();
    return EXPR.equals(node.kind()) && operands(node, operands) && operands.size() > 1
        ? operands
        : List.of();
  }

  /** The site of a concatenation or f-string node, when a value part follows a {@code ?}. */
  static Optional<FlowSite> of(final SyntaxNode node) {
    final List<Span> parts = EXPR.equals(node.kind()) ? concatenation(node) : fString(node);
    return parts.isEmpty()
        ? Optional.empty()
        : Optional.of(
            new FlowSite(
                FlowSiteKind.QUERY_PART,
                QUERY,
                "",
                Optional.empty(),
                List.of(),
                parts,
                node.span()
            )
        );
  }

  private static List<Span> concatenation(final SyntaxNode node) {
    final List<SyntaxNode> operands = operandsOf(node);
    final List<Span> after = new ArrayList<>();
    boolean query = false;
    for (final SyntaxNode operand : operands) {
      if (isString(operand)) {
        query |= text(operand).indexOf(MARK) >= 0;
      } else if (query) {
        after.add(operand.span());
      }
    }
    return after;
  }

  /** Flattens {@code a + b + c}, which the grammar nests to the left; false for other operators. */
  private static boolean operands(final SyntaxNode node, final List<SyntaxNode> out) {
    final List<SyntaxNode> parts = node.children();
    final boolean sum =
        EXPR.equals(node.kind())
            && parts.size() == PythonSyntax.THREE_PARTS
            && PLUS.equals(PythonSyntax.text(parts.get(1)));
    if (!sum) {
      out.add(node);
      return true;
    }
    return operands(parts.get(0), out) && operands(parts.get(2), out);
  }

  private static String text(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    PythonSyntax.tokens(node).forEach(token -> text.append(PythonSyntax.text(token)));
    return text.toString();
  }

  private static boolean isString(final SyntaxNode part) {
    return PLAIN_STRING.matcher(text(part)).matches();
  }

  private static List<Span> fString(final SyntaxNode node) {
    final String text = PythonSyntax.text(node);
    if (!node.children().isEmpty() || !PythonFStrings.isFString(text)) {
      return List.of();
    }
    final int mark = literalMark(text);
    final List<Span> after = new ArrayList<>();
    if (mark >= 0) {
      for (final PythonFStrings.Use use : PythonFStrings.names(text)) {
        if (use.from() > mark) {
          after.add(PythonSpans.within(node.span(), text, use.from(), use.to()));
        }
      }
    }
    return after;
  }

  /** The index of the first {@code ?} outside the replacement fields, or -1. */
  private static int literalMark(final String text) {
    int depth = 0;
    for (int index = 0; index < text.length(); index++) {
      final char current = text.charAt(index);
      if (current == OPEN) {
        depth++;
      } else if (current == CLOSE) {
        depth--;
      } else if (current == MARK && depth == 0) {
        return index;
      }
    }
    return -1;
  }
}
