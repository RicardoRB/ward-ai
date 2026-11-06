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
  DATAFLOW
}
