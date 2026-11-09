package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Symbol;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the members of an exported enum or namespace from the tokens of its file, and how a file
 * uses a name bound to one: as {@code name.member}, or in any other way, which uses every member.
 */
final class NodeMemberScan {

  private static final String IDENTIFIER = "Identifier";
  private static final String STRING = "StringLiteral";
  private static final String DOT = ".";
  private static final String OPEN = "{";
  private static final String CLOSE = "}";
  private static final String COMMA = ",";
  private static final String EXPORT = "export";
  private static final String IMPORT = "import";
  private static final String ENUM = "enum";
  private static final String NAMESPACE = "namespace";
  private static final Set<String> DECLARATIONS =
      Set.of("const", "let", "var", "function", "class", "interface", "type", "enum", "namespace");

  private final NodeTokens tokens;

  NodeMemberScan(final NodeTokens tokens) {
    this.tokens = tokens;
  }

  /**
   * The members of the enum or namespace {@code name} that the file declares: an enum's members are
   * its names, a namespace's are its exported declarations.
   */
  List<Symbol> declaredMembers(final String name, final String keyword) {
    final List<Symbol> members = new ArrayList<>();
    for (int i = 0; i + 2 < tokens.size(); i++) {
      if (tokens.isKeyword(i, keyword)
          && name.equals(tokens.text(i + 1))
          && tokens.isPunctuation(i + 2, OPEN)) {
        collectMembers(i + 2, keyword, members);
        break;
      }
    }
    return List.copyOf(members);
  }

  /** Adds the members between the brace at {@code open} and its close, at the outer depth only. */
  private void collectMembers(final int open, final String keyword, final List<Symbol> members) {
    int depth = 0;
    for (int j = open; j < tokens.size(); j++) {
      if (tokens.isPunctuation(j, OPEN)) {
        depth++;
      } else if (tokens.isPunctuation(j, CLOSE)) {
        depth--;
        if (depth == 0) {
          return;
        }
      } else if (depth == 1 && IDENTIFIER.equals(tokens.kind(j)) && isMember(j, keyword)) {
        members.add(new Symbol(tokens.text(j), tokens.leaf(j).span(), keyword));
      }
    }
  }

  private boolean isMember(final int j, final String keyword) {
    if (ENUM.equals(keyword)) {
      return tokens.isPunctuation(j - 1, OPEN) || tokens.isPunctuation(j - 1, COMMA);
    }
    return j >= 2 && tokens.isKeyword(j - 2, EXPORT) && DECLARATIONS.contains(tokens.text(j - 1));
  }

  /**
   * How the file uses {@code local}: the members reached as {@code local.member}, and whether it
   * uses the name in any other way (passed as a value, indexed, spread, or re-exported).
   */
  NodeFileFacts.MemberUsage usage(final String local) {
    final Set<String> members = new LinkedHashSet<>();
    boolean escaped = false;
    int i = 0;
    while (i < tokens.size()) {
      if (tokens.isKeyword(i, IMPORT)) {
        i = importEnd(i);
      } else {
        escaped = escaped || isUse(i, local) && !memberAccess(i, members);
      }
      i++;
    }
    return new NodeFileFacts.MemberUsage(Set.copyOf(members), escaped);
  }

  /**
   * The index of the module string that ends the import at {@code at}; the end if there is none.
   */
  private int importEnd(final int at) {
    int end = at;
    while (end < tokens.size() && !STRING.equals(tokens.kind(end))) {
      end++;
    }
    return end;
  }

  private boolean isUse(final int i, final String local) {
    return IDENTIFIER.equals(tokens.kind(i))
        && local.equals(tokens.text(i))
        && !isDeclarationOrProperty(i);
  }

  /** Records {@code local.member} at {@code i} and reports whether the token starts one. */
  private boolean memberAccess(final int i, final Set<String> members) {
    if (tokens.isPunctuation(i + 1, DOT)
        && i + 2 < tokens.size()
        && IDENTIFIER.equals(tokens.kind(i + 2))) {
      members.add(tokens.text(i + 2));
      return true;
    }
    return false;
  }

  private boolean isDeclarationOrProperty(final int i) {
    return i > 0
        && (tokens.isKeyword(i - 1, ENUM)
            || tokens.isKeyword(i - 1, NAMESPACE)
            || tokens.isPunctuation(i - 1, DOT));
  }
}
