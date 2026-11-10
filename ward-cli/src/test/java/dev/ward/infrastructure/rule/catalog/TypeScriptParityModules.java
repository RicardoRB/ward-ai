package dev.ward.infrastructure.rule.catalog;

import java.util.Set;

/**
 * Which TypeScript parity fixtures are linted as scripts and which get a module marker. Ward reads
 * a file with no import or export as a script, so the rows of script rules are linted as scripts.
 */
final class TypeScriptParityModules {

  private static final String MODULE_MARKER = "\nexport {};\n";
  private static final String NO_MARKER_ROW = "no-useless-empty-export";
  private static final Set<String> SCRIPT_ROWS = Set.of("no-redeclare");

  private TypeScriptParityModules() {}

  /** The fixture text: a module marker unless the row is a script or cannot take one. */
  static String text(final String row, final String body) {
    return NO_MARKER_ROW.equals(row) || SCRIPT_ROWS.contains(row) ? body : body + MODULE_MARKER;
  }

  /** The option that makes ESLint parse the row's fixtures as a script; empty for a module. */
  static String sourceType(final String row) {
    return SCRIPT_ROWS.contains(row) ? "sourceType: \"script\", " : "";
  }
}
