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
 * Reports a {@code tabIndex} on a non-interactive element that has no interactive role, at the
 * {@code tabIndex} attribute, as Biome 2.5.15's {@code noNoninteractiveTabindex} does. A negative
 * or unreadable tabIndex is left alone, and so is a dynamic role.
 */
public final class BiomeNoninteractiveTabindexCheck implements RuleCheck {

  private static final String TABINDEX = "tabIndex";
  private static final String ROLE = "role";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeNoninteractiveTabindexCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role's tokens are read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the non-interactive tabindex check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .flatMap(visit -> reported(visit.node(), unit.text()).stream())
        .map(attribute -> CheckSupport.at(attribute.span(), message))
        .toList();
  }

  /** The tabIndex attribute of the element when it is reported. */
  private Optional<SyntaxNode> reported(final SyntaxNode element, final String source) {
    if (!AriaElements.isNotInteractive(
        table, JsxAttributes.elementName(element), AriaElements.Attributes.of(element))) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> tabIndex = attributeNamed(element, TABINDEX);
    if (tabIndex.isEmpty() || !nonNegative(tabIndex.get(), source)) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> role = attributeNamed(element, ROLE);
    if (role.isEmpty()) {
      return tabIndex;
    }
    if (JsxAttributes.rawLiteral(role.get(), source).isEmpty()) {
      return Optional.empty();
    }
    return interactiveStaticRole(role.get(), source) ? Optional.empty() : tabIndex;
  }

  /** A static tabIndex that Rust's i32 parse reads as zero or more, or a number in braces. */
  private static boolean nonNegative(final SyntaxNode tabIndex, final String source) {
    return JsxAttributes.rawLiteral(tabIndex, source)
            .map(String::trim)
            .filter(text -> text.matches("\\+?\\d+"))
            .filter(BiomeNoninteractiveTabindexCheck::fitsI32)
            .isPresent()
        || JsxAttributes.value(tabIndex)
            .map(SyntaxWalk::text)
            .filter(text -> text.matches("\\{\\d+\\}"))
            .isPresent();
  }

  private static boolean fitsI32(final String text) {
    try {
      Integer.parseInt(text);
      return true;
    } catch (final NumberFormatException exception) {
      return false;
    }
  }

  private static Optional<SyntaxNode> attributeNamed(final SyntaxNode element, final String name) {
    return JsxAttributes.of(element).stream()
        .filter(attribute -> name.equals(JsxAttributes.name(attribute)))
        .findFirst();
  }

  /** Whether the first concrete token of a static role names an interactive role. */
  private boolean interactiveStaticRole(final SyntaxNode role, final String source) {
    return JsxAttributes.tokens(role, source).stream()
        .filter(table::isConcrete)
        .findFirst()
        .flatMap(table::role)
        .map(AriaRoleTable.AriaRoleRow::interactive)
        .orElse(false);
  }
}
