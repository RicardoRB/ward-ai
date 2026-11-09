package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the docstrings of Python modules, classes and functions, and reads their text. */
final class DocstringText {

  private static final String STRING = "STRING";
  private static final String EOF = "EOF";
  private static final String BLOCK = "block";
  private static final String ARGS_HEADER = "Args:";
  private static final String PERIOD = ".";
  private static final Pattern STRING_PREFIX = Pattern.compile("^[rRuUbB]*");
  private static final Pattern ENTRY_NAME = Pattern.compile("^\\*{0,2}(\\w+)");
  private static final String TRIPLE_QUOTE = "\"\"\"";
  private static final String TRIPLE_APOSTROPHE = "'''";
  private static final int TRIPLE_LENGTH = 3;
  private static final int QUOTE_LENGTH = 1;

  private DocstringText() {}

  /** The first leaf of {@code node} that is not whitespace and not the end of the file. */
  static Optional<SyntaxNode> firstLeaf(final SyntaxNode node) {
    return SyntaxWalk.leaves(node).stream()
        .filter(leaf -> !SyntaxWalk.text(leaf).isBlank() && !EOF.equals(leaf.kind()))
        .findFirst();
  }

  /** The docstring of a module, class or function: its first leaf when that is a string. */
  static Optional<SyntaxNode> docstring(final SyntaxNode scope) {
    return scope.children().stream()
        .filter(child -> BLOCK.equals(child.kind()))
        .findFirst()
        .flatMap(DocstringText::firstLeaf)
        .filter(DocstringText::isDocstring);
  }

  /** Every docstring of a unit: the module's and those of its classes and functions. */
  static List<SyntaxNode> docstrings(final SyntaxNode root) {
    final List<SyntaxNode> found = new ArrayList<>();
    firstLeaf(root).filter(DocstringText::isDocstring).ifPresent(found::add);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classdef", "funcdef"))) {
      docstring(visit.node()).ifPresent(found::add);
    }
    return found;
  }

  static boolean isDocstring(final SyntaxNode leaf) {
    return STRING.equals(leaf.kind());
  }

  /** The text inside the quotes of a string token, without its prefix. */
  static String body(final SyntaxNode leaf) {
    final String token = SyntaxWalk.text(leaf);
    final String text = STRING_PREFIX.matcher(token).replaceFirst("");
    final boolean triple = text.startsWith(TRIPLE_QUOTE) || text.startsWith(TRIPLE_APOSTROPHE);
    final int quote = triple ? TRIPLE_LENGTH : QUOTE_LENGTH;
    if (text.length() < 2 * quote) {
      return "";
    }
    return text.substring(quote, text.length() - quote);
  }

  /** Tells whether a docstring has no text inside its quotes. */
  static boolean isBlankBody(final SyntaxNode leaf) {
    return body(leaf).isBlank();
  }

  /** Tells whether a docstring has text whose summary line lacks a final period. */
  static boolean missingPeriod(final SyntaxNode leaf) {
    final String text = body(leaf);
    return !text.isBlank() && !periodEnds(text);
  }

  /** Tells whether the summary, the first line of a docstring, ends with a period. */
  static boolean periodEnds(final String text) {
    final String summary = text.strip().split("\n", 2)[0].strip();
    return summary.endsWith(PERIOD);
  }

  /** The names an Args section documents: the entries at the first entry's indentation. */
  static Set<String> documented(final String text) {
    final Set<String> names = new HashSet<>();
    final String[] lines = text.split("\n");
    for (int header = 0; header < lines.length; header++) {
      if (ARGS_HEADER.equals(lines[header].strip())) {
        addEntries(lines, header, names);
        break;
      }
    }
    return names;
  }

  private static void addEntries(final String[] lines, final int header, final Set<String> names) {
    final int headerIndent = indent(lines[header]);
    int entryIndent = -1;
    for (int line = header + 1; line < lines.length; line++) {
      if (lines[line].isBlank()) {
        continue;
      }
      final int indent = indent(lines[line]);
      if (indent <= headerIndent) {
        return;
      }
      entryIndent = entryIndent < 0 ? indent : entryIndent;
      if (indent == entryIndent) {
        final Matcher entry = ENTRY_NAME.matcher(lines[line].strip());
        if (entry.find()) {
          names.add(entry.group(1));
        }
      }
    }
  }

  private static int indent(final String line) {
    int count = 0;
    while (count < line.length() && line.charAt(count) == ' ') {
      count++;
    }
    return count;
  }
}
