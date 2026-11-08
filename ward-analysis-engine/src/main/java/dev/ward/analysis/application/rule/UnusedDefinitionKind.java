package dev.ward.analysis.application.rule;

/**
 * The declaration kinds the unused-definition rules report. A kind names the symbol kind it reads
 * from a summary, and whether the rule reports only names some file assigns.
 */
enum UnusedDefinitionKind {
  /** A function or method that no file uses. */
  FUNCTION("function", false),
  /** A class that no file uses. */
  CLASS("class", false),
  /** A stored property that some file assigns but no file reads. */
  PROPERTY("property", true);

  private final String symbolKind;
  private final boolean assignedOnly;

  UnusedDefinitionKind(final String symbolKind, final boolean assignedOnly) {
    this.symbolKind = symbolKind;
    this.assignedOnly = assignedOnly;
  }

  /** The symbol kind a summary gives a declaration of this kind. */
  String symbolKind() {
    return symbolKind;
  }

  /** Whether the rule reports only names that some file assigns. */
  boolean assignedOnly() {
    return assignedOnly;
  }

  /** The message of a finding about {@code name}. */
  String message(final String name) {
    if (assignedOnly) {
      return "property '" + name + "' is assigned but never read in any file";
    }
    return "unused " + symbolKind + " '" + name + "' is not used in any file";
  }
}
