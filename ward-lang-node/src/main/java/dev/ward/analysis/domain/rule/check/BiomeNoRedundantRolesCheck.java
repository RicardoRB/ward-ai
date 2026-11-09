package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code role} attribute that repeats the element's implicit role, at the role's value,
 * as Biome does. The implicit role comes from the mirrored table; a dynamic role or element name is
 * never reported.
 */
public final class BiomeNoRedundantRolesCheck implements RuleCheck {

  private static final String ROLE = "role";

  private final AriaRoleTable table;
  private final String message;

  /**
   * Creates the check.
   *
   * @param table the ARIA role table
   * @param message the message of each finding
   */
  public BiomeNoRedundantRolesCheck(final AriaRoleTable table, final String message) {
    this.table = Objects.requireNonNull(table, "table");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the role's tokens are read from the source text, which only the unit gives. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the redundant-roles check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .flatMap(visit -> redundant(visit.node(), unit.text()).stream())
        .map(value -> CheckSupport.at(value.span(), message))
        .toList();
  }

  /** The role's value when it repeats the element's implicit role. */
  private Optional<SyntaxNode> redundant(final SyntaxNode element, final String source) {
    final List<SyntaxNode> attributes = JsxAttributes.of(element);
    final Map<String, String> literals = new HashMap<>();
    attributes.forEach(
        attribute ->
            JsxAttributes.literal(attribute)
                .ifPresent(value -> literals.put(JsxAttributes.name(attribute), value)));
    final Set<String> present =
        attributes.stream().map(JsxAttributes::name).collect(java.util.stream.Collectors.toSet());
    final Optional<String> implicit =
        table.implicitRole(JsxAttributes.elementName(element), present::contains, literals);
    final Optional<SyntaxNode> roleAttribute =
        attributes.stream()
            .filter(attribute -> ROLE.equals(JsxAttributes.name(attribute)))
            .findFirst();
    if (implicit.isEmpty() || roleAttribute.isEmpty()) {
      return Optional.empty();
    }
    final Optional<String> explicit =
        explicitRole(JsxAttributes.tokens(roleAttribute.get(), source));
    return explicit
        .filter(implicit.get()::equals)
        .flatMap(role -> JsxAttributes.value(roleAttribute.get()));
  }

  /** The first token that names a concrete role, as Biome's from_roles reads it. */
  private Optional<String> explicitRole(final List<String> tokens) {
    return tokens.stream().filter(table::isConcrete).findFirst();
  }
}
