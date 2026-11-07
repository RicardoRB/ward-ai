package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each node of the given kinds by where it sits in the tree: outside every enclosing node
 * of the allowed kinds, or inside one, searching outward and stopping at a boundary kind. Covers
 * pyflakes {@code F701} and {@code F702} (a {@code break} or {@code continue} outside a loop),
 * {@code PLE0704} (a bare {@code raise} outside a handler), {@code PLE1142} (an {@code await}
 * outside an async function) and {@code PLE1700} (a {@code yield from} inside one).
 */
public final class AncestorCheck implements RuleCheck {

  /** Whether a node must sit outside the allowed kinds or inside them. */
  public enum Mode {
    /** Reported when no allowed ancestor encloses it. */
    OUTSIDE,
    /** Reported when an allowed ancestor encloses it. */
    INSIDE
  }

  private final Set<String> kinds;
  private final Set<String> allowed;
  private final Set<String> stop;
  private final Mode mode;
  private final Optional<Pattern> text;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the node kinds to report
   * @param allowed the enclosing kinds that make a node allowed (or, for {@link Mode#INSIDE}, that
   *     make it reported)
   * @param stop the enclosing kinds at which the search ends without finding an allowed kind; a
   *     stop kind whose parent is an allowed kind, like the body of an {@code async def}, does not
   *     end it
   * @param mode the placement that is reported
   * @param text a pattern the node's text must match, if present
   * @param message the message of each finding
   */
  public AncestorCheck(
      final Set<String> kinds,
      final Set<String> allowed,
      final Set<String> stop,
      final Mode mode,
      final Optional<Pattern> text,
      final String message) {
    this.kinds = Set.copyOf(kinds);
    this.allowed = Set.copyOf(allowed);
    this.stop = Set.copyOf(stop);
    this.mode = Objects.requireNonNull(mode, "mode");
    this.text = Objects.requireNonNull(text, "text");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      if (matchesText(visit.node()) && reported(enclosed(visit.ancestors()))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private boolean matchesText(final SyntaxNode node) {
    return text.map(pattern -> pattern.matcher(SyntaxWalk.text(node)).find()).orElse(true);
  }

  private boolean reported(final boolean enclosed) {
    return mode == Mode.OUTSIDE ? !enclosed : enclosed;
  }

  /** Whether an allowed kind encloses the node, searching outward from the nearest ancestor. */
  private boolean enclosed(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if (allowed.contains(kind)) {
        return true;
      }
      if (stop.contains(kind)) {
        return index > 0 && allowed.contains(ancestors.get(index - 1).kind());
      }
    }
    return false;
  }
}
