package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each node of the filter's kinds that lacks what the {@link Requirement} asks for. Every
 * non-empty part of the requirement must hold.
 */
public final class RequiredNodeCheck implements UnitCheck {

  private final NodeFilter filter;
  private final Requirement requirement;
  private final String message;

  /** Creates the check. */
  public RequiredNodeCheck(
      final NodeFilter filter, final Requirement requirement, final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.requirement = Objects.requireNonNull(requirement, "requirement");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return filter.select(root, file).stream()
        .filter(v -> !requirement.met(v))
        .map(v -> CheckSupport.at(v.node().span(), message))
        .toList();
  }

  /**
   * What a node must have.
   *
   * @param child kinds of which a direct child must exist, if not empty
   * @param descendant kinds of which a descendant must exist, if not empty
   * @param skip kinds whose subtrees the descendant search does not enter
   * @param sibling kinds of which a sibling must exist, if not empty
   */
  public record Requirement(
      Set<String> child, Set<String> descendant, Set<String> skip, Set<String> sibling) {

    /**
     * Copies the sets.
     *
     * @throws IllegalArgumentException if no part asks for anything
     */
    public Requirement {
      child = Set.copyOf(child);
      descendant = Set.copyOf(descendant);
      skip = Set.copyOf(skip);
      sibling = Set.copyOf(sibling);
      if (child.isEmpty() && descendant.isEmpty() && sibling.isEmpty()) {
        throw new IllegalArgumentException("a requirement needs a child, descendant or sibling");
      }
    }

    boolean met(final SyntaxWalk.Visit visit) {
      final SyntaxNode node = visit.node();
      return childMet(node) && descendantMet(node) && siblingMet(visit);
    }

    private boolean childMet(final SyntaxNode node) {
      return child.isEmpty() || hasChild(node, child);
    }

    private boolean descendantMet(final SyntaxNode node) {
      return descendant.isEmpty() || hasDescendant(node);
    }

    private boolean siblingMet(final SyntaxWalk.Visit visit) {
      return sibling.isEmpty() || hasSibling(visit);
    }

    private static boolean hasChild(final SyntaxNode node, final Set<String> kinds) {
      return node.children().stream().anyMatch(c -> kinds.contains(c.kind()));
    }

    private boolean hasDescendant(final SyntaxNode node) {
      return node.children().stream()
          .anyMatch(
              c -> descendant.contains(c.kind()) || !skip.contains(c.kind()) && hasDescendant(c));
    }

    private boolean hasSibling(final SyntaxWalk.Visit visit) {
      final List<SyntaxNode> up = visit.ancestors();
      return !up.isEmpty()
          && up.get(up.size() - 1).children().stream()
              .anyMatch(c -> !c.equals(visit.node()) && sibling.contains(c.kind()));
    }
  }
}
