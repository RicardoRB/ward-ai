package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a handler on a non-interactive element, at the element, as Biome 2.5.15's {@code
 * noNoninteractiveElementInteractions} does. Components, content-editable elements, presentation
 * roles, hidden elements, interactive roles and dialogs are silent.
 */
public final class BiomeNoninteractiveElementInteractionsCheck implements RuleCheck {

  private static final List<String> TYPES = List.of("focus", "image", "keyboard", "mouse");

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeNoninteractiveElementInteractionsCheck(
      final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the roles are read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the element-interaction check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> reported(visit.node(), unit.text()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private boolean reported(final SyntaxNode element, final String source) {
    if (AriaElements.component(element)
        || !AriaElements.hasEventHandler(table, TYPES, element)
        || AriaElements.contentEditable(element)
        || presentationRole(element, source)
        || AriaElements.hidden(element)
        || AriaElements.roleOf(table, element, source).map(this::interactive).orElse(false)
        || dialog(element)) {
      return false;
    }
    return AriaElements.isNotInteractive(
        table, JsxAttributes.elementName(element), AriaElements.Attributes.of(element));
  }

  /** Biome exempts a dialog by its implicit role, which the element has without any role. */
  private boolean dialog(final SyntaxNode element) {
    final AriaElements.Attributes attributes = AriaElements.Attributes.of(element);
    return table
        .implicitRole(JsxAttributes.elementName(element), attributes.has(), attributes.literals())
        .filter("dialog"::equals)
        .isPresent();
  }

  private boolean interactive(final String role) {
    return table.role(role).map(AriaRoleTable.AriaRoleRow::interactive).orElse(false);
  }

  /** Whether the role attribute is presentation or none, as its raw value. */
  private static boolean presentationRole(final SyntaxNode element, final String source) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> "role".equals(JsxAttributes.name(attribute)))
        .findFirst()
        .flatMap(attribute -> JsxAttributes.rawLiteral(attribute, source))
        .filter(value -> Set.of("presentation", "none").contains(value))
        .isPresent();
  }
}
