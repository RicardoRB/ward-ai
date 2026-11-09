package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.Symbol;
import java.util.Map;
import java.util.Optional;

/**
 * The Swift unused-declaration and assign-only-property rules. The summarizer declares only the
 * functions and stored properties that no protocol, superclass, extension, actor or attribute can
 * fix, so no declaration is exempt here. A {@code CodingKeys} enum declares neither, so it is never
 * reported.
 */
final class SwiftUnusedDefinitions implements UnusedDefinitionStrategy {

  /** The rule id for unused Swift functions that no protocol, superclass or attribute fixes. */
  static final String DECLARATION_ID = "ward:swift/unused-declaration";

  /** The rule id for Swift stored properties that are assigned but never read. */
  static final String ASSIGN_ONLY_ID = "ward:swift/assign-only-property";

  private static final Map<String, UnusedDefinitionKind> KINDS =
      Map.of(
          DECLARATION_ID, UnusedDefinitionKind.FUNCTION,
          ASSIGN_ONLY_ID, UnusedDefinitionKind.PROPERTY);
  private static final String SWIFT_SUFFIX = ".swift";

  @Override
  public Optional<UnusedDefinitionKind> kindOf(final String ruleId) {
    return Optional.ofNullable(KINDS.get(ruleId));
  }

  @Override
  public boolean scans(final String path) {
    return path.endsWith(SWIFT_SUFFIX);
  }

  @Override
  public boolean exempt(final Symbol symbol) {
    return false;
  }
}
