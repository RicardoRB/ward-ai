package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a meta, html, script or style element that has a role or a known aria-* attribute, at the
 * element, as Biome 2.5.15's {@code noAriaUnsupportedElements} does.
 */
public final class BiomeAriaUnsupportedElementsCheck implements RuleCheck {

  private static final Set<String> UNSUPPORTED = Set.of("meta", "html", "script", "style");
  private static final String ROLE = "role";
  private static final String PREFIX = "aria-";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table, which names the known aria-* attributes
   * @param message the message of each finding
   */
  public BiomeAriaUnsupportedElementsCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> UNSUPPORTED.contains(JsxAttributes.elementName(visit.node())))
        .filter(
            visit ->
                JsxAttributes.of(visit.node()).stream()
                    .map(JsxAttributes::name)
                    .anyMatch(this::knownRoleOrAria))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /** A role, or an aria-* attribute the table knows. */
  private boolean knownRoleOrAria(final String name) {
    return ROLE.equals(name) || name.startsWith(PREFIX) && table.attribute(name).isPresent();
  }
}
