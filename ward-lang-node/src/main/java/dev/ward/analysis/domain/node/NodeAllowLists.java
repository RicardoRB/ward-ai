package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the allow-list tests of a Node body: {@code LIST.includes(x)} and {@code LIST.has(x)}, where
 * {@code LIST} is a literal array or a constant table of the file, and the ternary {@code
 * LIST.includes(x) ? x : 'default'} that gives the tested value or a constant.
 */
final class NodeAllowLists {
  private static final String NAME = "([A-Za-z_$][\\w$]*)";
  private static final String LITERAL = "(?:'[^']*'|\"[^\"]*\"|-?\\d+(?:\\.\\d+)?)";
  private static final String LIST =
      "(\\[(?:" + LITERAL + "(?:," + LITERAL + ")*,?)?]|" + "[A-Za-z_$][\\w$]*)";
  private static final Pattern LIST_TEST =
      Pattern.compile("^(!?)" + LIST + "\\.(?:includes|has)\\(" + NAME + "\\)$");
  private static final Pattern ALLOWED_TERNARY =
      Pattern.compile(
          "^(?:const|let|var)" + NAME + "(?::[^=]+)?=" + LIST + "\\.(?:includes|has)\\(("
              + "[A-Za-z_$][\\w$]*(?:\\.[\\w$]+|\\(" + LITERAL + "?\\)|\\[-?\\d+])*"
              + ")\\)\\?\\3:" + LITERAL + ";?$"
      );
  private static final int NEGATION_GROUP = 1;
  private static final int LIST_GROUP = 2;
  private static final int VALUE_GROUP = 3;

  private final Set<String> tables;

  NodeAllowLists(final Set<String> tables) {
    this.tables = Set.copyOf(tables);
  }

  /** The declared name when the statement is {@code const v = LIST.includes(e) ? e : 'constant'}. */
  Optional<String> ternary(final String statement) {
    final Matcher match = ALLOWED_TERNARY.matcher(statement);
    return match.matches() && constantList(match.group(LIST_GROUP))
        ? Optional.of(match.group(1))
        : Optional.empty();
  }

  /** The name that an allow-list test of the wanted polarity checks, if the part is one. */
  Optional<String> tested(final SyntaxNode part, final boolean negated) {
    final Matcher test = LIST_TEST.matcher(NodeFlowSyntax.text(part));
    return test.matches()
        && test.group(NEGATION_GROUP).isEmpty() != negated
        && constantList(test.group(LIST_GROUP))
        ? Optional.of(test.group(VALUE_GROUP))
        : Optional.empty();
  }

  private boolean constantList(final String list) {
    return list.startsWith("[") || tables.contains(list);
  }
}
