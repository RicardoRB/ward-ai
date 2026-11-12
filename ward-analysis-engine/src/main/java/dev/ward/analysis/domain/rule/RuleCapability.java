package dev.ward.analysis.domain.rule;

/**
 * What a language frontend can provide to a rule. A rule declares the one capability it needs and
 * runs only when its frontend provides it.
 */
public enum RuleCapability {
  /** The concrete syntax tree of a unit. */
  SYNTAX,
  /** Declared symbols and their scopes. */
  SYMBOLS,
  /** The lexical scopes of a unit and the declaration each name refers to. */
  SCOPES,
  /** Resolved types. */
  TYPES,
  /** Control-flow graphs. */
  CFG,
  /** Data-flow facts. */
  DATAFLOW,
  /** The path and text of a file, with no syntax tree and no language. */
  TEXT;

  /**
   * Reports whether a rule with this capability sees the files around its unit, so its findings
   * depend on them as well as on the unit.
   */
  public boolean readsDependencies() {
    return this == SYMBOLS || this == SCOPES;
  }
}
