package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the first attribute of each name that an element assigns more than once, as Biome does.
 * Names are compared as written; a spread between the attributes does not change that.
 */
public final class BiomeDuplicatePropsCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the attribute's text
   */
  public BiomeDuplicatePropsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .flatMap(visit -> duplicates(visit.node()).stream())
        .map(
            attribute ->
                CheckSupport.at(
                    attribute.span(), CheckSupport.fill(message, SyntaxWalk.text(attribute), "")))
        .toList();
  }

  /** The first attribute of each repeated name, in source order. */
  private static List<SyntaxNode> duplicates(final SyntaxNode element) {
    final Map<String, SyntaxNode> first = new HashMap<>();
    final Set<String> reported = new HashSet<>();
    final List<SyntaxNode> out = new ArrayList<>();
    for (final SyntaxNode attribute : JsxAttributes.of(element)) {
      final String name = JsxAttributes.name(attribute);
      if (first.containsKey(name)) {
        if (reported.add(name)) {
          out.add(first.get(name));
        }
      } else {
        first.put(name, attribute);
      }
    }
    return out;
  }
}
