package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.Symbol;
import java.util.Optional;
import java.util.Set;

/**
 * How one language's unused-definition rules pick what they report: which of its rules report which
 * kind of declaration, which files they scan and which declarations they leave alone.
 */
interface UnusedDefinitionStrategy {

  /** The kind of declaration the rule {@code ruleId} reports, or empty if the language has none. */
  Optional<UnusedDefinitionKind> kindOf(String ruleId);

  /** Whether the rules scan the file at {@code path}. */
  boolean scans(String path);

  /** Whether {@code symbol} is exempt from every unused-definition rule of the language. */
  boolean exempt(Symbol symbol);

  /**
   * The names the project uses only inside a conformance clause, given every used name. A language
   * that records no such names keeps the default, which is none.
   */
  default Set<String> conformanceNames(final Set<String> usedNames) {
    return Set.of();
  }
}
