package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.Symbol;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Swift unused-declaration, assign-only-property and redundant-protocol rules. The summarizer
 * declares only the functions, stored properties and non-public protocols that no protocol,
 * superclass, extension, actor or attribute can fix, so no declaration is exempt here. A {@code
 * CodingKeys} enum declares none of them, so it is never reported.
 */
final class SwiftUnusedDefinitions implements UnusedDefinitionStrategy {

  /** The rule id for unused Swift functions that no protocol, superclass or attribute fixes. */
  static final String DECLARATION_ID = "ward:swift/unused-declaration";

  /** The rule id for Swift stored properties that are assigned but never read. */
  static final String ASSIGN_ONLY_ID = "ward:swift/assign-only-property";

  /** The rule id for Swift protocols that are used only to conform to. */
  static final String REDUNDANT_PROTOCOL_ID = "ward:swift/redundant-protocol";

  /**
   * The prefix the Swift summarizer gives a name used only in a conformance clause. The summarizer
   * keeps the same literal; a fixture of redundant-protocol fails if the two drift apart.
   */
  static final String CONFORMANCE_PREFIX = "conformance:";

  /** The rule id for Swift public or open declarations that only their own module uses. */
  static final String REDUNDANT_ACCESSIBILITY_ID = "ward:swift/redundant-public-accessibility";

  /**
   * The kind prefix the Swift summarizer gives a public or open symbol in the exports. The
   * summarizer keeps the same literal; a fixture of the rule fails if the two drift apart.
   */
  static final String PUBLIC_KIND_PREFIX = "public:";

  private static final Map<String, UnusedDefinitionKind> KINDS =
      Map.of(
          DECLARATION_ID, UnusedDefinitionKind.FUNCTION,
          ASSIGN_ONLY_ID, UnusedDefinitionKind.ASSIGNED_PROPERTY,
          REDUNDANT_PROTOCOL_ID, UnusedDefinitionKind.PROTOCOL);
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

  @Override
  public Set<String> conformanceNames(final Set<String> usedNames) {
    return usedNames.stream()
        .filter(name -> name.startsWith(CONFORMANCE_PREFIX))
        .map(name -> name.substring(CONFORMANCE_PREFIX.length()))
        .collect(Collectors.toUnmodifiableSet());
  }
}
