package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the syntax tree of a small Java source for the import and module checks. Each line holds
 * one package, import or module directive, and a module body ends at a line holding only a closing
 * brace. The shape matches the Java grammar: the keyword leaves, a {@code qualifiedName} for each
 * name, and a {@code moduleDirective} per directive.
 */
final class ImportTrees {

  private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\S");
  private static final String STAR = "*";
  private static final String PACKAGE_WORD = "package";
  private static final String IMPORT_WORD = "import";
  private static final String MODULE_WORD = "module";
  private static final int PLAIN_IMPORT = 1;
  private static final int KEYWORD_IMPORT = 2;
  private static final String DOT = ".";
  private static final String SEMICOLON = ";";
  private static final String CLOSE = "}";
  private static final String IDENTIFIER = "identifier";
  private static final String QUALIFIED = "qualifiedName";

  private ImportTrees() {}

  /** A token of a line, with its absolute offset and its 1-based line and column. */
  private record Token(String text, int offset, int line, int column) {

    SyntaxNode leaf(final String kind) {
      return new SyntaxNode(
          kind,
          new Span(line, column, line, column + text.length(), offset, offset + text.length()),
          Map.of("text", text),
          List.of());
    }
  }

  /** The unit of {@code source}: its package, imports and modules, in source order. */
  static SyntaxNode unit(final String source) {
    final List<SyntaxNode> children = new ArrayList<>();
    final List<SyntaxNode> module = new ArrayList<>();
    boolean open = false;
    int offset = 0;
    final String[] lines = source.split("\n", -1);
    for (int index = 0; index < lines.length; index++) {
      final String text = lines[index];
      final List<Token> tokens = tokens(text, offset, index + 1);
      if (!tokens.isEmpty()) {
        final String first = tokens.get(0).text();
        if (open && CLOSE.equals(first)) {
          module.add(tokens.get(0).leaf("'}'"));
          children.add(module(module, tokens.get(0)));
          module.clear();
          open = false;
        } else if (open) {
          module.add(directive(tokens));
        } else if (PACKAGE_WORD.equals(first)) {
          children.add(packageLine(tokens));
        } else if (IMPORT_WORD.equals(first)) {
          children.add(importLine(tokens));
        } else if (MODULE_WORD.equals(first)) {
          open = true;
          module.add(tokens.get(0).leaf("'module'"));
          module.add(name(tokens.subList(1, tokens.size() - 1)));
          module.add(tokens.get(tokens.size() - 1).leaf("'{'"));
        }
      }
      offset += text.length() + 1;
    }
    return new SyntaxNode(
        "compilationUnit", new Span(1, 1, lines.length, 1, 0, source.length()), Map.of(), children);
  }

  private static SyntaxNode packageLine(final List<Token> tokens) {
    final List<SyntaxNode> children = new ArrayList<>();
    children.add(tokens.get(0).leaf("'package'"));
    children.add(name(tokens.subList(1, tokens.size() - 1)));
    children.add(tokens.get(tokens.size() - 1).leaf("';'"));
    return node("packageDeclaration", tokens, children);
  }

  private static SyntaxNode importLine(final List<Token> tokens) {
    final List<SyntaxNode> children = new ArrayList<>();
    children.add(tokens.get(0).leaf("'import'"));
    final int index = keywordLength(tokens);
    if (index != PLAIN_IMPORT) {
      children.add(tokens.get(1).leaf("'" + tokens.get(1).text() + "'"));
    }
    final int end = nameEnd(tokens, index);
    children.add(name(tokens.subList(index, end)));
    for (final Token token : tokens.subList(end, tokens.size())) {
      children.add(token.leaf(SEMICOLON.equals(token.text()) ? "';'" : token.text()));
    }
    return node("importDeclaration", tokens, children);
  }

  /** Returns 2 when the import has a static or module keyword, else 1. */
  private static int keywordLength(final List<Token> tokens) {
    final String word = tokens.size() > 2 ? tokens.get(1).text() : "";
    return "static".equals(word) || "module".equals(word) ? KEYWORD_IMPORT : PLAIN_IMPORT;
  }

  /** Returns the index after the last name token: before a semicolon, a star or a dot-star. */
  private static int nameEnd(final List<Token> tokens, final int start) {
    int end = start;
    while (end < tokens.size()) {
      final String text = tokens.get(end).text();
      final boolean dotStar = DOT.equals(text) && STAR.equals(tokens.get(end + 1).text());
      if (SEMICOLON.equals(text) || STAR.equals(text) || dotStar) {
        break;
      }
      end++;
    }
    return end;
  }

  private static SyntaxNode directive(final List<Token> tokens) {
    final List<SyntaxNode> children = new ArrayList<>();
    for (final Token token : tokens) {
      children.add(token.leaf("'" + token.text() + "'"));
    }
    return node("moduleDirective", tokens, children);
  }

  private static SyntaxNode module(final List<SyntaxNode> children, final Token close) {
    final Span first = children.get(0).span();
    return new SyntaxNode(
        "moduleDeclaration",
        new Span(
            first.startLine(),
            first.startColumn(),
            close.line(),
            close.column() + 1,
            first.startOffset(),
            close.offset() + 1),
        Map.of(),
        children);
  }

  /** A qualified name made of identifier leaves and dots, or nothing when the list is empty. */
  private static SyntaxNode name(final List<Token> tokens) {
    final List<SyntaxNode> children = new ArrayList<>();
    for (final Token token : tokens) {
      final boolean identifier = Pattern.matches("[A-Za-z_][A-Za-z0-9_]*", token.text());
      children.add(token.leaf(identifier ? IDENTIFIER : token.text()));
    }
    final Token first = tokens.get(0);
    final Token last = tokens.get(tokens.size() - 1);
    return new SyntaxNode(
        QUALIFIED,
        new Span(
            first.line(),
            first.column(),
            last.line(),
            last.column() + last.text().length(),
            first.offset(),
            last.offset() + last.text().length()),
        Map.of(),
        children);
  }

  private static SyntaxNode node(
      final String kind, final List<Token> tokens, final List<SyntaxNode> children) {
    final Token first = tokens.get(0);
    final Token last = tokens.get(tokens.size() - 1);
    return new SyntaxNode(
        kind,
        new Span(
            first.line(),
            first.column(),
            last.line(),
            last.column() + last.text().length(),
            first.offset(),
            last.offset() + last.text().length()),
        Map.of(),
        children);
  }

  private static List<Token> tokens(final String text, final int offset, final int line) {
    final List<Token> tokens = new ArrayList<>();
    final Matcher matcher = TOKEN.matcher(text);
    while (matcher.find()) {
      tokens.add(new Token(matcher.group(), offset + matcher.start(), line, matcher.start() + 1));
    }
    return List.copyOf(tokens);
  }
}
