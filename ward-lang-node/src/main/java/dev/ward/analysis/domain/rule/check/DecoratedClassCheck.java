package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports the nodes of the given kinds that sit in a decorated class, such as an Angular component
 * with {@code @Component(...)}. The class decorators, the member's decorators and shape, the node's
 * own text and place, and the alias of a decorator argument are each optional; a condition that is
 * set must hold for the node to be reported.
 */
public final class DecoratedClassCheck implements RuleCheck {

  private final Set<String> kinds;
  private final DecoratedFilters conditions;
  private final String message;

  /**
   * The conditions on the enclosing class: a class decorator that must match, pairs of a class
   * decorator and the node text it allows, and an interface the class must not implement.
   *
   * @param decorator a pattern a class decorator must match, if present
   * @param entries pairs of a class decorator pattern and a node text pattern; the node must match
   *     one pair, if any pair is given
   * @param implementsNot the interface name the class must not implement, if present
   */
  public record ClassCondition(
      Optional<Pattern> decorator, Map<Pattern, Pattern> entries, Optional<String> implementsNot) {

    /** Copies the entries. */
    public ClassCondition {
      entries = Map.copyOf(entries);
    }
  }

  /**
   * The conditions on the class member that holds the node.
   *
   * @param decorator a pattern a decorator of the member must match, if present
   * @param has kinds of which the member must have a direct child, if not empty
   * @param notText a pattern the member's text must not match, if present
   */
  public record MemberCondition(
      Optional<Pattern> decorator, Set<String> has, Optional<Pattern> notText) {

    /** Copies the kinds. */
    public MemberCondition {
      has = Set.copyOf(has);
    }
  }

  /**
   * The conditions on the node's own text and on what the node contains.
   *
   * @param text a pattern the node's text must match, if present
   * @param exempt a pattern whose matches are never reported, if present
   * @param inClassDecorator whether the node must sit inside a decorator of its class
   * @param notInsideText a pattern no enclosing node's text may match, if present
   */
  public record NodeCondition(
      Optional<Pattern> text,
      Optional<Pattern> exempt,
      boolean inClassDecorator,
      Optional<Pattern> notInsideText) {}

  /**
   * The conditions on the place of the node in the tree.
   *
   * @param inside kinds of which some ancestor must be, if not empty
   * @param parent kinds of which the parent must be, if not empty
   * @param notChild kinds of which the node must have no direct child
   */
  public record ScopeCondition(Set<String> inside, Set<String> parent, Set<String> notChild) {

    /** Copies the sets. */
    public ScopeCondition {
      inside = Set.copyOf(inside);
      parent = Set.copyOf(parent);
      notChild = Set.copyOf(notChild);
    }
  }

  /**
   * The conditions on the name the node carries.
   *
   * @param heritageMissing for a node whose text is a key, the interface its class must implement;
   *     the node is reported only when the class does not implement it, and only for these keys
   * @param alias a pattern whose first group is the alias of the node, if present
   * @param allowed aliases that are never reported
   * @param aria whether an {@code aria-*} alias is allowed when it names the member in camel case
   */
  public record NameCondition(
      Map<String, String> heritageMissing,
      Optional<Pattern> alias,
      Set<String> allowed,
      boolean aria) {

    /** Copies the maps and sets. */
    public NameCondition {
      heritageMissing = Map.copyOf(heritageMissing);
      allowed = Set.copyOf(allowed);
    }
  }

  /**
   * All the conditions of the check.
   *
   * @param enclosing the conditions on the enclosing class
   * @param member the conditions on the class member
   * @param node the conditions on the node's text
   * @param scope the conditions on the node's place
   * @param name the conditions on the node's name
   */
  public record Filters(
      ClassCondition enclosing,
      MemberCondition member,
      NodeCondition node,
      ScopeCondition scope,
      NameCondition name) {}

  /**
   * Creates the check.
   *
   * @param kinds the node kinds to look at
   * @param filters the conditions every reported node meets
   * @param message the message of each finding
   */
  public DecoratedClassCheck(final Set<String> kinds, final Filters filters, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.conditions = new DecoratedFilters(filters);
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, kinds).stream()
        .filter(visit -> conditions.holds(new DecoratedSite(visit)))
        .map(visit -> new RuleMatch(visit.node().span(), message))
        .toList();
  }
}
