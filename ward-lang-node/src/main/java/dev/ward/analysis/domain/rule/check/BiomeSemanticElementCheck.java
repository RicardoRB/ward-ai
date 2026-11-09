package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a static role that an HTML element of the role would express, at the role attribute, as
 * Biome 2.5.15's {@code useSemanticElements} does. A component, a custom element, an element that
 * already is one of the role's elements, and the roles Biome excludes are silent.
 */
public final class BiomeSemanticElementCheck implements RuleCheck {

  private static final String ROLE = "role";
  private static final Set<String> EXCLUDED_ROLES =
      Set.of("img", "combobox", "listbox", "option", "status", "alert");

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeSemanticElementCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role is read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the semantic-elements check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> reported(visit.node(), unit.text()))
        .map(visit -> roleAttribute(visit.node()).orElseThrow())
        .map(role -> CheckSupport.at(role.span(), message))
        .toList();
  }

  private boolean reported(final SyntaxNode element, final String source) {
    if (AriaElements.component(element) || AriaElements.customElement(element)) {
      return false;
    }
    final Optional<SyntaxNode> role = roleAttribute(element);
    if (role.isEmpty()
        || JsxAttributes.rawLiteral(role.get(), source).isEmpty()
        || EXCLUDED_ROLES.contains(JsxAttributes.rawLiteral(role.get(), source).get())) {
      return false;
    }
    final Optional<String> name = table.firstConcrete(JsxAttributes.tokens(role.get(), source));
    if (name.isEmpty()) {
      return false;
    }
    final String elementName = JsxAttributes.elementName(element);
    return table.semanticElements(name.get()).stream()
        .noneMatch(
            concept -> concept.element().equals(elementName) && attributesMatch(element, concept));
  }

  private static boolean attributesMatch(
      final SyntaxNode element, final AriaRoleTable.ElementConcept concept) {
    return concept.attributes().entrySet().stream()
        .allMatch(
            required ->
                JsxAttributes.of(element).stream()
                    .filter(attribute -> required.getKey().equals(JsxAttributes.name(attribute)))
                    .anyMatch(
                        attribute ->
                            JsxAttributes.literal(attribute)
                                .filter(required.getValue()::equals)
                                .isPresent()));
  }

  private static Optional<SyntaxNode> roleAttribute(final SyntaxNode element) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> ROLE.equals(JsxAttributes.name(attribute)))
        .findFirst();
  }
}
