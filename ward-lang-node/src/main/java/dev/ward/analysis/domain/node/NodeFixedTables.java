package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the top-level constants of a file whose value is a table of constants: an object, array,
 * {@code new Map([...])} or {@code new Set([...])} written with literals only. Whatever key a
 * lookup in such a table uses, the value it returns is one of the members of the table, so it is a
 * value of the program and not one that the key carries.
 *
 * <p>shortcut: a table is read from the text of its declaration and holds no spread, call or
 * reference; upgrade when a project builds its tables from other constants.
 */
final class NodeFixedTables {
  private static final Pattern DECLARATION =
      Pattern.compile("^(?:export)?const([\\w$]+)(?::[^=]+)?=(.+?);?$");
  private static final Pattern STRINGS = Pattern.compile("'[^']*'|\"[^\"]*\"|`[^`$]*`");
  private static final Pattern KEYS = Pattern.compile("[A-Za-z_$][\\w$]*(?=:)");
  private static final Pattern WORDS = Pattern.compile("new(?:Map|Set)|true|false|null|undefined");
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$]");
  private static final Pattern SHAPE = Pattern.compile("^(?:new(?:Map|Set))?[\\[{(].*[\\]})]$");
  private static final int NAME_GROUP = 1;
  private static final int VALUE_GROUP = 2;

  private NodeFixedTables() {}

  /** The names of the constant tables that the top level of the file declares. */
  static Set<String> of(final SyntaxNode program) {
    final Set<String> found = new HashSet<>();
    for (final SyntaxNode statement : NodeFlowSyntax.statements(program)) {
      final Matcher declaration = DECLARATION.matcher(NodeFlowSyntax.text(statement));
      if (declaration.matches() && isTable(declaration.group(VALUE_GROUP))) {
        found.add(declaration.group(NAME_GROUP));
      }
    }
    return found;
  }

  private static boolean isTable(final String value) {
    final String bare = STRINGS.matcher(value).replaceAll("0");
    final String unkeyed = KEYS.matcher(bare).replaceAll("0");
    final String plain = WORDS.matcher(unkeyed).replaceAll("0");
    return SHAPE.matcher(bare).matches() && !IDENTIFIER.matcher(plain).find();
  }
}
