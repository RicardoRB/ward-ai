package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a lower-case JSX element that has a handler without one of its partner handlers, at the
 * element, as Biome does. A spread does not silence it. Each pair is written {@code
 * trigger:partner} or {@code trigger:partner1|partner2} when any of several partners will do.
 */
public final class BiomeJsxHandlerPairCheck implements RuleCheck {

  private static final String PAIR_SEPARATOR = ":";
  private static final String PARTNER_SEPARATOR = "\\|";

  private final List<Pair> pairs;
  private final List<JsxElementSpec> exempt;
  private final String message;

  /** A trigger handler and the partner handlers, any one of which must be present. */
  private record Pair(String trigger, Set<String> partners) {

    static Pair parse(final String text) {
      final int separator = text.indexOf(PAIR_SEPARATOR);
      return new Pair(
          text.substring(0, separator),
          Set.of(text.substring(separator + 1).split(PARTNER_SEPARATOR)));
    }
  }

  /**
   * Creates the check.
   *
   * @param pairs the handler pairs, each {@code trigger:partner} or {@code trigger:p1|p2}
   * @param exempt the element specs that are never reported
   * @param message the message of each finding
   */
  public BiomeJsxHandlerPairCheck(
      final List<String> pairs, final List<String> exempt, final String message) {
    this.pairs = pairs.stream().map(Pair::parse).toList();
    this.exempt = exempt.stream().map(JsxElementSpec::parse).toList();
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> isDomElement(visit.node()))
        .filter(visit -> exempt.stream().noneMatch(spec -> spec.selects(visit.node())))
        .filter(visit -> pairs.stream().anyMatch(pair -> unpaired(visit.node(), pair)))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean isDomElement(final SyntaxNode element) {
    final String name = JsxAttributes.elementName(element);
    return !name.isEmpty() && Character.isLowerCase(name.charAt(0)) && !name.contains(".");
  }

  /** Whether the element has the trigger and none of the partners. */
  private static boolean unpaired(final SyntaxNode element, final Pair pair) {
    final List<String> names = JsxAttributes.of(element).stream().map(JsxAttributes::name).toList();
    return names.contains(pair.trigger()) && pair.partners().stream().noneMatch(names::contains);
  }
}
