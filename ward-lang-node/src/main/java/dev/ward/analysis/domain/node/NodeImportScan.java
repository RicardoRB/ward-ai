package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the names the import declarations of one file bind, from its tokens. Tokens are used rather
 * than the tree because the TypeScript grammar does not parse {@code import type} and {@code import
 * { type A }}, and reads them as unrelated statements.
 */
final class NodeImportScan {

  /**
   * One name an import binds.
   *
   * @param local the token of the name as the file uses it
   * @param member the imported name, {@code default} for a default import, or {@code *}
   * @param specifier the module as written, without quotes
   * @param typeOnly whether the import exists only for the type system
   * @param site the region from {@code import} to the module string
   */
  record Binding(SyntaxNode local, String member, String specifier, boolean typeOnly, Span site) {}

  private static final Pattern WORD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final String STRING = "StringLiteral";
  private static final String TYPE = "type";
  private static final String FROM = "from";
  private static final String AS = "as";
  private static final String COMMA = ",";
  private static final String OPEN = "{";
  private static final String CLOSE = "}";
  private static final String STAR = "*";
  private static final String EQUALS = "=";
  private static final String REQUIRE = "require";
  private static final Set<String> DEFAULT_FOLLOWERS = Set.of(",", "from", "=");
  private static final int REQUIRE_STRING_OFFSET = 3;

  private final NodeTokens tokens;
  private final List<Binding> bindings = new ArrayList<>();
  private final List<Span> sites = new ArrayList<>();

  NodeImportScan(final SyntaxNode tree) {
    this.tokens = new NodeTokens(NodeLeaves.of(tree));
    for (int index = 0; index < tokens.size(); index++) {
      if (tokens.isKeyword(index, "import") && tokens.isStatementKeyword(index)) {
        scan(index);
      }
    }
  }

  /** The names the imports bind, in source order. */
  List<Binding> bindings() {
    return List.copyOf(bindings);
  }

  /** The regions of the import declarations, in source order. */
  List<Span> sites() {
    return List.copyOf(sites);
  }

  private void scan(final int start) {
    int index = start + 1;
    if (STRING.equals(tokens.kind(index))) {
      sites.add(skip(start, index));
      return;
    }
    final boolean typeOnly = isTypeKeyword(index);
    index += typeOnly ? 1 : 0;
    final List<SyntaxNode> names = new ArrayList<>();
    final List<String> members = new ArrayList<>();
    final List<Boolean> types = new ArrayList<>();
    if (isWord(index) && DEFAULT_FOLLOWERS.contains(tokens.text(index + 1))) {
      if (EQUALS.equals(tokens.text(index + 1))) {
        requireForm(start, index);
        return;
      }
      names.add(tokens.leaf(index));
      members.add("default");
      types.add(typeOnly);
      index++;
      index += COMMA.equals(tokens.text(index)) ? 1 : 0;
    }
    index = clause(index, names, members, types);
    while (index < tokens.size() && !STRING.equals(tokens.kind(index))) {
      index++;
    }
    if (index >= tokens.size()) {
      return;
    }
    final Span site = site(start, index);
    final String specifier = NodeTokens.unquote(tokens.text(index));
    sites.add(skip(start, index));
    for (int slot = 0; slot < names.size(); slot++) {
      bindings.add(
          new Binding(
              names.get(slot), members.get(slot), specifier, typeOnly || types.get(slot), site));
    }
  }

  private boolean isTypeKeyword(final int index) {
    return TYPE.equals(tokens.text(index))
        && (OPEN.equals(tokens.text(index + 1))
            || STAR.equals(tokens.text(index + 1))
            || isWord(index + 1) && !FROM.equals(tokens.text(index + 1)));
  }

  private boolean isString(final int index) {
    return STRING.equals(tokens.kind(index));
  }

  private boolean isWord(final int index) {
    return WORD.matcher(tokens.text(index)).matches() && !STRING.equals(tokens.kind(index));
  }

  /** Reads {@code * as ns} or {@code { a, b as c, type d }} from {@code index}; returns the end. */
  private int clause(
      final int index,
      final List<SyntaxNode> names,
      final List<String> members,
      final List<Boolean> types) {
    if (STAR.equals(tokens.text(index)) && AS.equals(tokens.text(index + 1))) {
      names.add(tokens.leaf(index + 2));
      members.add(STAR);
      types.add(Boolean.FALSE);
      return index + REQUIRE_STRING_OFFSET;
    }
    if (!OPEN.equals(tokens.text(index))) {
      return index;
    }
    int cursor = index + 1;
    while (cursor < tokens.size() && !CLOSE.equals(tokens.text(cursor))) {
      cursor = specifier(cursor, names, members, types);
    }
    return cursor + 1;
  }

  private int specifier(
      final int start,
      final List<SyntaxNode> names,
      final List<String> members,
      final List<Boolean> types) {
    int cursor = start;
    final boolean type =
        TYPE.equals(tokens.text(cursor))
            && isWord(cursor + 1)
            && !AS.equals(tokens.text(cursor + 1));
    cursor += type ? 1 : 0;
    if (isWord(cursor) || STRING.equals(tokens.kind(cursor))) {
      final String member = NodeTokens.unquote(tokens.text(cursor));
      final int local = AS.equals(tokens.text(cursor + 1)) ? cursor + 2 : cursor;
      names.add(tokens.leaf(local));
      members.add(member);
      types.add(type);
      cursor = local;
    }
    cursor++;
    return COMMA.equals(tokens.text(cursor)) ? cursor + 1 : cursor;
  }

  /** {@code import x = require('m')}. */
  private void requireForm(final int start, final int name) {
    if (REQUIRE.equals(tokens.text(name + 2)) && isString(name + REQUIRE_STRING_OFFSET + 1)) {
      final int literal = name + REQUIRE_STRING_OFFSET + 1;
      final Span site = site(start, literal);
      sites.add(skip(start, literal));
      bindings.add(
          new Binding(
              tokens.leaf(name), "*", NodeTokens.unquote(tokens.text(literal)), false, site));
    }
  }

  /** The region to leave out of the tree walk: the import and its closing semicolon. */
  private Span skip(final int from, final int to) {
    return site(from, ";".equals(tokens.text(to + 1)) ? to + 1 : to);
  }

  private Span site(final int from, final int to) {
    final Span first = tokens.leaf(from).span();
    final Span last = tokens.leaf(to).span();
    return new Span(
        first.startLine(),
        first.startColumn(),
        last.endLine(),
        last.endColumn(),
        first.startOffset(),
        last.endOffset());
  }
}
