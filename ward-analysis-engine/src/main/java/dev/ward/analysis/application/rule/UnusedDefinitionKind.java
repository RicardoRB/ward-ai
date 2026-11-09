package dev.ward.analysis.application.rule;

import java.util.Set;

/**
 * The declaration kinds the unused-definition rules report. A kind names the symbol kind it reads
 * from a summary, and the mode decides which names of the project it reports.
 */
enum UnusedDefinitionKind {
  /** A function or method that no file uses. */
  FUNCTION("function", Mode.UNUSED),
  /** A class that no file uses. */
  CLASS("class", Mode.UNUSED),
  /** A method that no file uses. */
  METHOD("method", Mode.UNUSED),
  /** An instance attribute that no file uses. */
  ATTRIBUTE("attribute", Mode.UNUSED),
  /** A Python property that no file uses. */
  UNUSED_PROPERTY("property", Mode.UNUSED),
  /** A stored property that some file assigns but no file reads. */
  ASSIGNED_PROPERTY("property", Mode.ASSIGNED_ONLY),
  /** A protocol that some file conforms to but no file uses as a type. */
  PROTOCOL("protocol", Mode.CONFORMANCE_ONLY);

  /** Which names of the project a kind reports. */
  enum Mode {
    /** Names no file uses. */
    UNUSED,
    /** Names some file assigns and no file reads. */
    ASSIGNED_ONLY,
    /** Names some file uses only to conform to, and no file uses otherwise. */
    CONFORMANCE_ONLY
  }

  private final String symbolKind;
  private final Mode mode;

  UnusedDefinitionKind(final String symbolKind, final Mode mode) {
    this.symbolKind = symbolKind;
    this.mode = mode;
  }

  /** The symbol kind a summary gives a declaration of this kind. */
  String symbolKind() {
    return symbolKind;
  }

  /**
   * Whether the rule reports {@code name}, given the names the project uses plainly, assigns, and
   * uses only to conform to.
   */
  boolean reports(
      final String name,
      final Set<String> used,
      final Set<String> assigned,
      final Set<String> conformance) {
    if (used.contains(name)) {
      return false;
    }
    return switch (mode) {
      case UNUSED -> true;
      case ASSIGNED_ONLY -> assigned.contains(name);
      case CONFORMANCE_ONLY -> conformance.contains(name);
    };
  }

  /** The message of a finding about {@code name}. */
  String message(final String name) {
    return switch (mode) {
      case UNUSED -> "unused " + symbolKind + " '" + name + "' is not used in any file";
      case ASSIGNED_ONLY -> "property '" + name + "' is assigned but never read in any file";
      case CONFORMANCE_ONLY ->
          "protocol '" + name + "' is only used to conform to, never as a type";
    };
  }
}
