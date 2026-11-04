package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the module references of one file from its tokens: imports, re-exports, requires, imports.
 */
final class NodeReferenceScan {
  static final String STATIC = "static:";
  static final String TYPE = "type:";
  static final String REQUIRE = "require:";
  static final String DYNAMIC = "dynamic:";

  private static final String IMPORT = "import";
  private static final String EXPORT = "export";
  private static final String FROM = "from";
  private static final String TYPE_WORD = "type";
  private static final String REQUIRE_WORD = "require";
  private static final String STAR = "*";
  private static final String COMMA = ",";
  private static final String CLOSE_PAREN = ")";
  private static final String STRING = "StringLiteral";
  private static final int CLOSE_OFFSET = 3;

  private final NodeTokens tokens;
  private final NodeExportScan exports;
  private final List<Reference> references = new ArrayList<>();
  private int statement = -1;

  /** Creates the scan over {@code tokens}, recording re-exported names in {@code exports}. */
  NodeReferenceScan(final NodeTokens tokens, final NodeExportScan exports) {
    this.tokens = tokens;
    this.exports = exports;
  }

  List<Reference> references() {
    return List.copyOf(references);
  }

  /** Records the token at {@code i}. */
  void token(final int i) {
    if (tokens.isStatementKeyword(i)) {
      statement = i;
      startStatement(i);
    }
    scanCall(i);
    scanFrom(i);
  }

  /** Records a side-effect import, and the names of an export statement. */
  private void startStatement(final int i) {
    if (tokens.isKeyword(i, IMPORT) && STRING.equals(tokens.kind(i + 1))) {
      add(STATIC, tokens.text(i + 1), tokens.leaf(i + 1).span());
    }
    if (tokens.isKeyword(i, EXPORT)) {
      exports.statement(i);
    }
  }

  /** Records {@code require("x")} and {@code import("x")} with one string literal argument. */
  private void scanCall(final int i) {
    if (!tokens.isCall(i)
        || !STRING.equals(tokens.kind(i + 2))
        || !CLOSE_PAREN.equals(tokens.text(i + CLOSE_OFFSET))) {
      return;
    }
    final String kind = tokens.isKeyword(i, REQUIRE_WORD) ? REQUIRE : DYNAMIC;
    add(kind, tokens.text(i + 2), tokens.leaf(i + 2).span());
  }

  /** Records {@code from "x"} of an import or re-export, and the names a star re-export adds. */
  private void scanFrom(final int i) {
    if (statement < 0 || !tokens.isKeyword(i, FROM) || !STRING.equals(tokens.kind(i + 1))) {
      return;
    }
    final String value = NodeTokens.unquote(tokens.text(i + 1));
    add(prefixOf(statement), tokens.text(i + 1), tokens.leaf(i + 1).span());
    final int star = starAt(statement);
    if (tokens.isKeyword(statement, EXPORT) && star >= 0) {
      exports.star(star, value);
    }
  }

  private void add(final String kind, final String literal, final Span span) {
    references.add(new Reference(kind + NodeTokens.unquote(literal), span));
  }

  private int starAt(final int declaration) {
    if (STAR.equals(tokens.text(declaration + 1))) {
      return declaration + 1;
    }
    if (tokens.isKeyword(declaration + 1, TYPE_WORD) && STAR.equals(tokens.text(declaration + 2))) {
      return declaration + 2;
    }
    return -1;
  }

  /** Returns the kind prefix of a reference made by the statement that starts at {@code at}. */
  private String prefixOf(final int at) {
    return isTypeOnly(at) ? TYPE : STATIC;
  }

  private boolean isTypeOnly(final int at) {
    return tokens.isKeyword(at + 1, TYPE_WORD)
        && !FROM.equals(tokens.text(at + 2))
        && !COMMA.equals(tokens.text(at + 2))
        && !"=".equals(tokens.text(at + 2));
  }
}
